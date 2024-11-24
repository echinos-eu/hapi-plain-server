package example;

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

      // Set response status
      response.setStatus(proxyResponse.getStatusCodeValue());

      // Forward headers, excluding Transfer-Encoding
      proxyResponse.getHeaders().forEach((key, values) -> {
        if (!HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(key)) { // Exclude Transfer-Encoding
          for (String value : values) {
            response.addHeader(key, value);
          }
        }
      });
      System.out.println("Response Headers: " + proxyResponse.getHeaders());
      System.out.println("Response Body: " + proxyResponse.getBody());

      // Write the response body
      byte[] responseBody = proxyResponse.getBody();
      response.getOutputStream().write(responseBody);
      response.getOutputStream().flush();
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
