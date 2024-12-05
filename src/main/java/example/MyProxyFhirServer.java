package example;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.RestfulServer;
import example.provider.PatientProvider;
import interceptor.ProxyInterceptor;
import jakarta.servlet.annotation.WebServlet;

@WebServlet(urlPatterns = {"/*"}, displayName = "FHIR Proxy Server")
public class MyProxyFhirServer extends RestfulServer {


  public MyProxyFhirServer() {
    super(FhirContext.forR4());
  }

  @Override
  protected void initialize() {
    registerInterceptor(new ProxyInterceptor());
    registerProvider(new PatientProvider());
  }
}