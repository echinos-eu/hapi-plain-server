package example;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.springframework.http.HttpHeaders;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.server.RestfulServer;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Enumeration;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@WebServlet(urlPatterns = {"/*"}, displayName = "FHIR Proxy Server")
public class MyProxyFhirServer extends RestfulServer {

  private static final String TARGET_SERVER_BASE = "https://fhir.echinos.eu/fhir";
  private final RestTemplate restTemplate = new RestTemplate();

  public MyProxyFhirServer() {
    super(FhirContext.forR4());
  }

  @Override
  protected void initialize() {
    getInterceptorService().registerInterceptor(new ProxyInterceptor());
  }

  @Interceptor
  private class ProxyInterceptor {

    @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
    public boolean incomingRequestPreProcessed(HttpServletRequest request,
        HttpServletResponse response) {
      // Derive target URL based on incoming request path
      String targetUrl = TARGET_SERVER_BASE + request.getRequestURI();

      try {
        handleRequest(targetUrl, request, response);
      } catch (IOException e) {
        e.printStackTrace();
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        try {
          response.getWriter().write("Error occurred while processing the request.");
        } catch (IOException ioException) {
          ioException.printStackTrace();
        }
      }

      return false;  // Stop further processing by HAPI
    }

    private void handleRequest(String targetUrl, HttpServletRequest request,
        HttpServletResponse response) throws IOException {
      HttpMethod httpMethod = getHttpMethod(request.getMethod());

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
    boolean isGzipEncoded = "gzip".equalsIgnoreCase(proxyResponse.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING));

    // Decompress the response body if gzip-encoded
    byte[] responseBodyBytes = proxyResponse.getBody();
    String responseBody;
    if (isGzipEncoded) {
        responseBody = new String(decompressGzip(responseBodyBytes), StandardCharsets.UTF_8);
    } else {
        responseBody = new String(responseBodyBytes, StandardCharsets.UTF_8);
    }

    // Modify the response body
    String targetUrlBase = TARGET_SERVER_BASE;
    String proxyUrlBase = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
    responseBody = responseBody.replace(targetUrlBase, proxyUrlBase);

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
      switch (method.toUpperCase()) {
        case "GET":
          return HttpMethod.GET;
        case "POST":
          return HttpMethod.POST;
        case "PUT":
          return HttpMethod.PUT;
        case "DELETE":
          return HttpMethod.DELETE;
        case "PATCH":
          return HttpMethod.PATCH;
        case "HEAD":
          return HttpMethod.HEAD;
        case "OPTIONS":
          return HttpMethod.OPTIONS;
        case "TRACE":
          return HttpMethod.TRACE;
        default:
          throw new UnsupportedOperationException("Unsupported HTTP method: " + method);
      }
    }
  }
}
