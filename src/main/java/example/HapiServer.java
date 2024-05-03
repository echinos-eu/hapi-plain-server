package example;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import example.provider.PatientProvider;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.annotation.WebServlet;

@WebServlet("/*")
public class HapiServer extends RestfulServer {

  public HapiServer() {
    super(FhirContext.forR4());
  }
  @Override
  public void initialize() {
    registerProvider(new PatientProvider());

    //Interceptoren
    registerInterceptor(new ResponseHighlighterInterceptor());
  }
}
