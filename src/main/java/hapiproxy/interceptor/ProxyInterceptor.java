package hapiproxy.interceptor;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Interceptor class for proxying HTTP requests.
 */
@Slf4j
@Component
@Interceptor
public class ProxyInterceptor {

  @Value("${proxy.target-server-base}")
  private String targetServerBase;  // Target server base URL

  /**
   * Hook method to process incoming requests before they are handled by the server.
   *
   * @param request  the incoming HTTP request
   * @param response the HTTP response
   * @return false if the request should not be further processed, true otherwise
   */
  @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
  public boolean incomingRequestPreProcessed(final HttpServletRequest request,
      final HttpServletResponse response) {
    // Derive target URL based on incoming request path and query string
    StringBuilder targetUrlBuilder = new StringBuilder(targetServerBase).append(
        request.getRequestURI());
    String queryString = request.getQueryString();
    if (queryString != null && !queryString.isEmpty()) {
      targetUrlBuilder.append("?").append(queryString);
    }
    String targetUrl = targetUrlBuilder.toString();
    log.info("Derived target URL: {}", targetUrl);
    try {
      return handleRequest(targetUrl, request, response);
    } catch (final IOException e) {
      log.error("Error occurred while processing the request.", e);
      throw new InternalErrorException("Error occurred while processing the request.", e);
    }
  }

  /**
   * Handles the forwarding of the HTTP request to the target server.
   *
   * @param targetUrl the target URL to forward the request to
   * @param request   the incoming HTTP request
   * @param response  the HTTP response
   * @return false if the request should not be further processed, true otherwise
   * @throws IOException if an I/O error occurs
   */
  private boolean handleRequest(final String targetUrl, final HttpServletRequest request,
      final HttpServletResponse response) throws IOException {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    final HttpMethod httpMethod = getHttpMethod(request.getMethod());

    if (httpMethod == HttpMethod.PATCH) {
      throw new InternalErrorException("PATCH method is not supported.");
    }

    // Check if this request should be intercepted
    if (httpMethod == HttpMethod.POST || httpMethod == HttpMethod.PUT
        || httpMethod == HttpMethod.DELETE ||
        (httpMethod == HttpMethod.GET && isFhirOperation(request))) {
      log.info("Intercepting {} request: {}", getFullUrl(request), request.getMethod());
      boolean furtherProcessing = intercept(request, response, httpMethod, targetUrl,
          authentication);
      if (!furtherProcessing) {
        return false;
      }
    }

    log.info("Forwarding {} request: {}", getFullUrl(request), request.getMethod());
    // Build headers using Spring's HttpHeaders
    final HttpHeaders headers = buildHeaders(request);

    // Get request body for POST/PUT methods
    byte[] requestBody = null;
    if (httpMethod == HttpMethod.POST || httpMethod == HttpMethod.PUT) {
      requestBody = request.getInputStream().readAllBytes();
      headers.remove(HttpHeaders.TRANSFER_ENCODING); // Ensure chunked encoding is not forwarded
    }

    // Build the request entity
    final org.springframework.http.HttpEntity<byte[]> entity = new org.springframework.http.HttpEntity<>(
        requestBody, headers);

    // Execute the request using RestTemplate
    RestTemplate restTemplate = new RestTemplate();
    final ResponseEntity<byte[]> proxyResponse = restTemplate.exchange(URI.create(targetUrl),
        httpMethod, entity, byte[].class);

    // Check for gzip encoding
    final boolean isGzipEncoded = "gzip".equalsIgnoreCase(
        proxyResponse.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING));

    // Decompress the response body if gzip-encoded
    byte[] responseBodyBytes = proxyResponse.getBody();
    String responseBody;
    if (isGzipEncoded) {
      responseBody = new String(decompressGzip(responseBodyBytes), StandardCharsets.UTF_8);
    } else {
      responseBody = new String(responseBodyBytes, StandardCharsets.UTF_8);
    }

    // Modify the response body
    final String proxyUrlBase =
        request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
    responseBody = responseBody.replace(targetServerBase, proxyUrlBase);

    // Recompress the body if it was originally gzip-encoded
    if (isGzipEncoded) {
      responseBodyBytes = compressGzip(responseBody.getBytes(StandardCharsets.UTF_8));
      response.setHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
    } else {
      responseBodyBytes = responseBody.getBytes(StandardCharsets.UTF_8);
      response.setHeader(HttpHeaders.CONTENT_ENCODING, "identity");
    }

    // Update Content-Length header
    response.setContentLength(responseBodyBytes.length);

    // Set response status
    response.setStatus(proxyResponse.getStatusCodeValue());

    // Forward headers, excluding Transfer-Encoding and adjusting Content-Encoding
    proxyResponse.getHeaders().forEach((key, values) -> {
      if (!HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(key) &&
          !HttpHeaders.CONTENT_ENCODING.equalsIgnoreCase(key)) { // Skip Content-Encoding
        for (final String value : values) {
          response.addHeader(key, value);
        }
      }
    });
    // Write the modified response body
    response.getOutputStream().write(responseBodyBytes);
    response.getOutputStream().flush();
    return false;
  }

  /**
   * Intercepts the request for additional processing.
   *
   * @param request        the incoming HTTP request
   * @param response       the HTTP response
   * @param httpMethod     the HTTP method of the request
   * @param targetUrl      the target URL to forward the request to
   * @param authentication the authentication object
   * @return true if the request should be forwarded, false otherwise
   */
  public boolean intercept(HttpServletRequest request, HttpServletResponse response,
      HttpMethod httpMethod, String targetUrl, Authentication authentication) {
    // Should the request be forwarded to the target server? Set to false to not forward.
    boolean forwardRequest = true;
    if (isFhirOperation(request)) {
      log.info("Handling FHIR operation call: {}", targetUrl);
      // handle FHIR Operation
    } else if (httpMethod == HttpMethod.POST) {
      log.info("Handling POST request: {}", targetUrl);
      //handle POST request
    } else if (httpMethod == HttpMethod.PUT) {
      log.info("Handling PUT request: {}", targetUrl);
      //handle PUT request
    } else if (httpMethod == HttpMethod.DELETE) {
      log.info("Handling DELETE request: {}", targetUrl);
      //handle DELETE request
    }
    return forwardRequest;
  }

  /**
   * Checks if the request is a FHIR operation.
   *
   * @param request the incoming HTTP request
   * @return true if the request is a FHIR operation, false otherwise
   */
  private boolean isFhirOperation(HttpServletRequest request) {
    String requestUri = request.getRequestURI();
    return requestUri != null && requestUri.contains("/$");
  }

  /**
   * Decompresses a GZIP-compressed byte array.
   *
   * @param compressed the compressed byte array
   * @return the decompressed byte array
   * @throws IOException if an I/O error occurs
   */
  private byte[] decompressGzip(final byte[] compressed) throws IOException {
    try (final ByteArrayInputStream byteStream = new ByteArrayInputStream(compressed);
        final GZIPInputStream gzipStream = new GZIPInputStream(byteStream);
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
      final byte[] buffer = new byte[1024];
      int len;
      while ((len = gzipStream.read(buffer)) > 0) {
        outStream.write(buffer, 0, len);
      }
      return outStream.toByteArray();
    }
  }

  /**
   * Compresses a byte array using GZIP.
   *
   * @param uncompressed the uncompressed byte array
   * @return the compressed byte array
   * @throws IOException if an I/O error occurs
   */
  private byte[] compressGzip(final byte[] uncompressed) throws IOException {
    try (final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        final GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream)) {
      gzipStream.write(uncompressed);
      gzipStream.finish();
      return byteStream.toByteArray();
    }
  }

  /**
   * Builds HTTP headers from the incoming request.
   *
   * @param request the incoming HTTP request
   * @return the HTTP headers
   */
  private HttpHeaders buildHeaders(final HttpServletRequest request) {
    final HttpHeaders headers = new HttpHeaders();

    final Enumeration<String> headerNames = request.getHeaderNames();
    if (headerNames != null) {
      while (headerNames.hasMoreElements()) {
        final String headerName = headerNames.nextElement();
        if (!isRestrictedHeader(headerName)) {
          final Enumeration<String> headerValues = request.getHeaders(headerName);
          while (headerValues.hasMoreElements()) {
            headers.add(headerName, headerValues.nextElement());
          }
        }
      }
    }
    return headers;
  }

  /**
   * Checks if the header is restricted and should not be forwarded.
   *
   * @param headerName the name of the header
   * @return true if the header is restricted, false otherwise
   */
  private boolean isRestrictedHeader(final String headerName) {
    return HttpHeaders.HOST.equalsIgnoreCase(headerName)
        || HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(headerName)
        || HttpHeaders.CONNECTION.equalsIgnoreCase(headerName);
  }

  /**
   * Converts a string representation of an HTTP method to an HttpMethod enum.
   *
   * @param method the string representation of the HTTP method
   * @return the HttpMethod enum
   */
  private HttpMethod getHttpMethod(final String method) {
    return switch (method.toUpperCase()) {
      case "GET" -> HttpMethod.GET;
      case "POST" -> HttpMethod.POST;
      case "PUT" -> HttpMethod.PUT;
      case "DELETE" -> HttpMethod.DELETE;
      case "PATCH" -> HttpMethod.PATCH;
      case "HEAD" -> HttpMethod.HEAD;
      case "OPTIONS" -> HttpMethod.OPTIONS;
      case "TRACE" -> HttpMethod.TRACE;
      default -> throw new UnsupportedOperationException("Unsupported HTTP method: " + method);
    };
  }

  /**
   * Constructs the full URL of the incoming request.
   *
   * @param request the incoming HTTP request
   * @return the full URL as a string
   */
  private String getFullUrl(final HttpServletRequest request) {
    final StringBuilder fullUrl = new StringBuilder();

    // Add scheme (http/https)
    fullUrl.append(request.getScheme())
        .append("://");

    // Add server name
    fullUrl.append(request.getServerName());

    // Add port if not default
    if ((request.getScheme().equals("http") && request.getServerPort() != 80) ||
        (request.getScheme().equals("https") && request.getServerPort() != 443)) {
      fullUrl.append(":").append(request.getServerPort());
    }

    // Add request URI
    fullUrl.append(request.getRequestURI());

    // Add query string, if present
    if (request.getQueryString() != null) {
      fullUrl.append("?").append(request.getQueryString());
    }
    return fullUrl.toString();
  }
}
