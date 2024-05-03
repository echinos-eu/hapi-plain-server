package example.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.Delete;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.sf.saxon.expr.Component.M;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;

public class PatientProvider implements IResourceProvider {

  private HashMap<String, Patient> patients = new HashMap<>();

  @Override
  public Class<? extends IBaseResource> getResourceType() {
    return Patient.class;
  }

  @Read
  public Patient read(@IdParam IdType id) {
    Patient patient = null;
    //VREAD
//      if (id.hasVersionIdPart()) {
//        //TODO: code for versioned read
//      }
//      else {
    patient = patients.get(id.getIdPart());
//      }
    return patient;
  }

  @Create
  public MethodOutcome create(@ResourceParam Patient patient) {
    String id = UUID.randomUUID().toString();
    patient.setId(id);
    patients.put(id, patient);
    MethodOutcome outcome = new MethodOutcome();
    outcome.setId(new IdType(patient.getResourceType().name(), id));
    return outcome;
  }

  @Update
  public MethodOutcome update(@IdParam IdType id, @ResourceParam Patient patient) {
    patients.put(id.getIdPart(), patient);
    return new MethodOutcome();
  }

  @Search
  public List<Patient> search(
      @OptionalParam(name = Patient.SP_FAMILY) StringParam familyName
  ) {
    List<Patient> list = null;
    if (Objects.isNull(familyName)) {
      list = new ArrayList<>(patients.values());
    } else {
      list = patients.values().stream()
          .filter(p -> p.getNameFirstRep().getFamily()
              .startsWith(familyName.getValue())).toList();
    }
    return list;
  }

  @Delete
  public MethodOutcome delete(@IdParam IdType id) {
    patients.remove(id.getIdPart());
    return new MethodOutcome();
  }
}
