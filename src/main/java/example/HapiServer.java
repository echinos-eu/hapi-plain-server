package example;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.RestfulServer;

public class HapiServer extends RestfulServer {


  public HapiServer(FhirContext ctx) {
    super(ctx);

  }

  @Override
  protected void initialize() {
  }
}
