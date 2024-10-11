package example;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;

import jakarta.servlet.annotation.WebServlet;

@WebServlet("/*")
public class HapiServer extends RestfulServer {

  public HapiServer() {
    super(FhirContext.forR4());
  }
  @Override
  public void initialize() {
    // add provider

    //Interceptoren
    registerInterceptor(new ResponseHighlighterInterceptor());
  }
}
