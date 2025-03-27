package hapiproxy;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.RestfulServer;
import hapiproxy.interceptor.ProxyInterceptor;
import jakarta.servlet.annotation.WebServlet;
import org.springframework.beans.factory.annotation.Autowired;

@WebServlet(urlPatterns = {"/*"}, displayName = "FHIR Proxy Server")
public class MyProxyFhirServer extends RestfulServer {

  @Autowired
  private ProxyInterceptor proxyInterceptor;

  public MyProxyFhirServer() {
    super(FhirContext.forR4());
  }

  @Override
  protected void initialize() {
    registerInterceptor(proxyInterceptor);
  }
}