package example.provider;

import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.Delete;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.NumberParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.HealthcareService;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;

public class HealthCareServiceProvider implements IResourceProvider {

  private HashMap<String, Patient> patients = new HashMap<>();

  @Override
  public Class<? extends IBaseResource> getResourceType() {
    return HealthcareService.class;
  }

  @Search(queryName = "healthCareServiceLocation")
  public List<HealthcareService> searchByNamedQuery(
      @RequiredParam(name = "latitude") StringParam latitude,
      @RequiredParam(name = "longitude") StringParam longitude,
      @OptionalParam(name = "distance") NumberParam distance) {
    System.out.println("latitude: " + latitude.getValue());
    System.out.println("longitude: " + longitude.getValue());
    System.out.println("distance: " + distance.getValue());

    List<HealthcareService> retVal = new ArrayList<>();
    // ...populate...
    return retVal;
  }

}
