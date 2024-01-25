package example.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.rest.annotation.Create;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
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
    //VREAD
//    if(id.hasVersionIdPart()) {
    //VREAD from DB
//    }
    Patient patient = patients.get(id.getIdPart());
    return patient;
  }

  @Create
  public MethodOutcome create(@ResourceParam Patient patient) {
    String id = UUID.randomUUID().toString();
    patient.setId(id);
    patients.put(id, patient);
    MethodOutcome outcome = new MethodOutcome();
    outcome.setId(new IdType(patient.getResourceType().name(), id));
    List<String> familyNameList = patient.getName().stream().map(n -> n.getFamily()).toList();
    System.out.println(familyNameList.get(0));
    FhirContext fhirContext = FhirContext.forR4Cached();
    IFhirPath fhirPath = fhirContext.newFhirPath();
    List<StringType> evaluate = fhirPath.evaluate(patient, "name.family", StringType.class);
    System.out.println("Fhirpath: " + evaluate.get(0).getValue());

    return outcome;
  }

  @Update
  public MethodOutcome update(@IdParam IdType id, @ResourceParam Patient patient) {
    patients.put(id.getIdPart(), patient);
    return new MethodOutcome();
  }

  @Search
  public List<Patient> search(
      @OptionalParam(name = Patient.SP_IDENTIFIER) TokenParam identifier,
      @OptionalParam(name = Patient.SP_FAMILY) StringParam familyName,
      @OptionalParam(name = Patient.SP_BIRTHDATE) DateParam birthdate) {
    List<Patient> list = patients.values().stream()
        .filter(p -> p.getNameFirstRep().getFamily().contains(familyName.getValue())).toList();
    return list;
  }
}
