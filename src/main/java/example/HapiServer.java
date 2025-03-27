package example;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import example.provider.PatientProvider;

public class HapiServer extends RestfulServer {


  private final PatientProvider patientProvider;

  public HapiServer(FhirContext ctx, PatientProvider patientProvider) {
    super(ctx);
    this.patientProvider = patientProvider;

  }

  @Override
  protected void initialize() {
    registerProvider(patientProvider);
    registerInterceptor(new ResponseHighlighterInterceptor());
  }
}
