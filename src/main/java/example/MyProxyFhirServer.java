package example;

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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import util.RequestBodyUtil;

@WebServlet(urlPatterns = {"/*"}, displayName = "FHIR Proxy Server")
public class MyProxyFhirServer extends RestfulServer {

  private static final String TARGET_SERVER_BASE = "https://fhir.echinos.eu/fhir";
  private final HttpClient httpClient = HttpClient.newHttpClient();

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
        HttpServletResponse response)
        throws IOException {
      HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl));

            // Forward all headers from the original request
            request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
                String headerValue = request.getHeader(headerName);
                if (headerValue != null) {
                    requestBuilder.header(headerName, headerValue);
                }
            });

      HttpRequest proxyRequest = switch (request.getMethod()) {
        case "GET" -> requestBuilder.GET().build();
        case "POST" -> {
          byte[] requestBody = RequestBodyUtil.getRequestBodyAsByteArray(request);
          yield requestBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(requestBody)).build();
        }
                case "PUT" -> {
                    byte[] requestBody = RequestBodyUtil.getRequestBodyAsByteArray(request);
                    yield requestBuilder.PUT(HttpRequest.BodyPublishers.ofByteArray(requestBody)).build();
                }
                case "DELETE" -> requestBuilder.DELETE().build();
        default -> throw new UnsupportedOperationException(
            "Unsupported HTTP method: " + request.getMethod());
      };

            HttpResponse<String> proxyResponse;
      try {
                proxyResponse = httpClient.send(proxyRequest, HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request to target server was interrupted", e);
            }

            // Return response from proxied server
        response.setStatus(proxyResponse.statusCode()); // Set status code
        proxyResponse.headers().map().forEach((key, values) -> {
          for (String value : values) {
            response.addHeader(key, value); // Add headers from proxied response
          }
        });
        response.getWriter().write(proxyResponse.body()); // Write body
    }
  }
}
