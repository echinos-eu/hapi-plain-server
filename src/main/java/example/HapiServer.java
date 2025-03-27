package example;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import example.provider.PatientProvider;
import example.provider.ValidationProvider;

public class HapiServer extends RestfulServer {


  private final PatientProvider patientProvider;
  private final ValidationProvider validationProvider;

  public HapiServer(FhirContext ctx, PatientProvider patientProvider, ValidationProvider validationProvider) {
    super(ctx);
    this.patientProvider = patientProvider;
    this.validationProvider = validationProvider;

  }

  @Override
  protected void initialize() {
    registerProvider(patientProvider);
    registerProvider(validationProvider);
    registerInterceptor(new ResponseHighlighterInterceptor());
  }
}
