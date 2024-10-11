package example.provider;

import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import com.fasterxml.jackson.annotation.JsonFormat.Features;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;

public class PatientProvider implements IResourceProvider {

  private HashMap<String, Patient> patients = new HashMap<>();

  @Override
  public Class<? extends IBaseResource> getResourceType() {
    return Patient.class;
  }

  @Read
  public Patient read(@IdParam IdType theId) {
    Patient patient = null;
    patient = patients.get(theId.getIdPart());
    return patient;
  }

  @Create
  public MethodOutcome create(@ResourceParam Patient patient) {
    String id = UUID.randomUUID().toString();
    patient.setId(id);
    patients.put(id, patient);
    MethodOutcome outcome = new MethodOutcome();
    outcome.setId(new IdType("Patient", id));
    return outcome;
  }

  @Search
  public List<Patient> search(
      @OptionalParam(name = Patient.SP_FAMILY) StringParam family
  ) {
    List<Patient> patientsReturn = null;
    if (Objects.isNull(family)) {
      patientsReturn = new ArrayList<>(patients.values());
    } else {
      patientsReturn = patients.values().stream().filter(
          p -> p.getNameFirstRep().getFamily().startsWith(family.getValue())
      ).toList();
    }
    return patientsReturn;
  }


}
