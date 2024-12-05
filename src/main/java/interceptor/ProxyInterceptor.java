package interceptor;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Interceptor
public class ProxyInterceptor {

  private static final String TARGET_SERVER_BASE = "https://fhir.echinos.eu/fhir";
  private final RestTemplate restTemplate = new RestTemplate();


  @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
  public boolean incomingRequestPreProcessed(HttpServletRequest request,
      HttpServletResponse response) {
    // Derive target URL based on incoming request path
    String targetUrl = TARGET_SERVER_BASE + request.getRequestURI();
    boolean furtherProcessing = false;
    try {
      furtherProcessing = handleRequest(targetUrl, request, response);
    } catch (IOException e) {
      e.printStackTrace();
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      try {
        response.getWriter().write("Error occurred while processing the request.");
      } catch (IOException ioException) {
        ioException.printStackTrace();
      }
    }
    return furtherProcessing;  // Stop further processing by HAPI
  }

  private boolean handleRequest(String targetUrl, HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    HttpMethod httpMethod = getHttpMethod(request.getMethod());

    // Allow only GET requests
//    if (httpMethod != HttpMethod.GET) {
//      return true;
//    }

    log.info("Forwarding GET request: {}", getFullUrl(request));
    // Build headers using Spring's HttpHeaders
    HttpHeaders headers = buildHeaders(request);

    // Get request body for POST/PUT methods
    byte[] requestBody = null;
    if (httpMethod == HttpMethod.POST || httpMethod == HttpMethod.PUT) {
      requestBody = request.getInputStream().readAllBytes();
      headers.remove(HttpHeaders.TRANSFER_ENCODING); // Ensure chunked encoding is not forwarded
    }

    // Build the request entity
    org.springframework.http.HttpEntity<byte[]> entity = new org.springframework.http.HttpEntity<>(
        requestBody, headers);

    // Execute the request using RestTemplate
    ResponseEntity<byte[]> proxyResponse = restTemplate.exchange(URI.create(targetUrl),
        httpMethod, entity, byte[].class);

    // Check for gzip encoding
    boolean isGzipEncoded = "gzip".equalsIgnoreCase(
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
    String proxyUrlBase =
        request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
    responseBody = responseBody.replace(TARGET_SERVER_BASE, proxyUrlBase);

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
        for (String value : values) {
          response.addHeader(key, value);
        }
      }
    });

    // Write the modified response body
    response.getOutputStream().write(responseBodyBytes);
    response.getOutputStream().flush();
    return false;
  }

  private byte[] decompressGzip(byte[] compressed) throws IOException {
    try (ByteArrayInputStream byteStream = new ByteArrayInputStream(compressed);
        GZIPInputStream gzipStream = new GZIPInputStream(byteStream);
        ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[1024];
      int len;
      while ((len = gzipStream.read(buffer)) > 0) {
        outStream.write(buffer, 0, len);
      }
      return outStream.toByteArray();
    }
  }

  private byte[] compressGzip(byte[] uncompressed) throws IOException {
    try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream)) {
      gzipStream.write(uncompressed);
      gzipStream.finish();
      return byteStream.toByteArray();
    }
  }


  private HttpHeaders buildHeaders(HttpServletRequest request) {
    HttpHeaders headers = new HttpHeaders();

    Enumeration<String> headerNames = request.getHeaderNames();
    if (headerNames != null) {
      while (headerNames.hasMoreElements()) {
        String headerName = headerNames.nextElement();
        if (!isRestrictedHeader(headerName)) {
          Enumeration<String> headerValues = request.getHeaders(headerName);
          while (headerValues.hasMoreElements()) {
            headers.add(headerName, headerValues.nextElement());
          }
        }
      }
    }
    return headers;
  }

  private boolean isRestrictedHeader(String headerName) {
    return HttpHeaders.HOST.equalsIgnoreCase(headerName)
        || HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(headerName)
        || HttpHeaders.CONNECTION.equalsIgnoreCase(headerName);
  }

  private HttpMethod getHttpMethod(String method) {
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

  private String getFullUrl(HttpServletRequest request) {
    StringBuilder fullUrl = new StringBuilder();

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
