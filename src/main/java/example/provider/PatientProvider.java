package example.provider;

import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.validation.ValidationOptions;
import ca.uhn.fhir.validation.ValidationResult;
import example.service.EAUValidator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

@Component
public class PatientProvider implements IResourceProvider {

  private final EAUValidator validator;
  private HashMap<String, Patient> patients = new HashMap<>();

  public PatientProvider(EAUValidator validator) {
    this.patients = patients;
    this.validator = validator;
  }

  @Override
  public Class<? extends IBaseResource> getResourceType() {
    return Patient.class;
  }

  @Create
  public MethodOutcome create(@ResourceParam Patient patient) {
    String id = UUID.randomUUID().toString();
    patient.setId(id);
    patients.put(id, patient);
    MethodOutcome methodOutcome = new MethodOutcome();
    methodOutcome.setId(new IdType("Patient", id));
    return methodOutcome;
  }

  @Read
  public Patient read(@IdParam IdType id) {
    Patient patient = null;
    patient = patients.get(id.getIdPart());
    return patient;
  }

  @Search
  public List<Patient> search(
      @OptionalParam(name = Patient.SP_FAMILY) StringParam familyName) {
    List<Patient> returnPats = null;
    if (Objects.isNull(familyName)) {
      returnPats = new ArrayList<>(patients.values());
    } else {
      returnPats = patients.values().stream()
          .filter(p -> p.getName().stream()
              .anyMatch(
                  n -> n.getFamily() != null && n.getFamily().startsWith(familyName.getValue())))
          .toList();

    }
    return returnPats;
  }

  @Operation(name = "$validate", idempotent = true)
  public MethodOutcome validate(@OperationParam(name = "resource") Patient patient)
  {
    ValidationOptions options = new ValidationOptions();
    options.addProfile("https://fhir.kbv.de/StructureDefinition/KBV_PR_FOR_Patient|1.2.0");
    ValidationResult validationResult = validator.validateWithResult(patient, options);
    MethodOutcome methodOutcome = new MethodOutcome();
    methodOutcome.setOperationOutcome(validationResult.toOperationOutcome());
    return methodOutcome;
  }
}
