package example.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.validation.ValidationResult;
import example.service.EAUValidator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import net.sf.saxon.expr.Component.M;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.ResourceType;
import org.springframework.stereotype.Component;

@Component
public class ValidationProvider {

  private final EAUValidator validator;
  private HashMap<String, Patient> patients = new HashMap<>();

  public ValidationProvider(EAUValidator validator) {
    this.validator = validator;
  }

  @Operation(name = "$validate", idempotent = true)
  public MethodOutcome validate(@OperationParam(name = "resource") Bundle res) {
    ValidationResult validationResult = validator.validateWithResult(res);
    MethodOutcome methodOutcome = new MethodOutcome();
    methodOutcome.setOperationOutcome(validationResult.toOperationOutcome());
    return methodOutcome;
  }

  @Operation(name = "$sendAbrechnung", idempotent = true)
  public MethodOutcome receiveInvoice(@OperationParam(name = "resource") Bundle res) {
    Optional<Practitioner> practitioner = res.getEntry().stream()
        .filter(e -> e.getResource().getResourceType().equals(ResourceType.Practitioner))
        .map(e -> (Practitioner) e.getResource()).findFirst();
    System.out.println(practitioner.get().getNameFirstRep().getFamily());
    return new MethodOutcome();
  }

  @Operation(name = "$sendAbrechnung2", idempotent = true)
  public MethodOutcome receiveInvoice2(@OperationParam(name = "resource") Bundle res) {
    FhirContext ctx = FhirContext.forR4Cached();
    IFhirPath iFhirPath = ctx.newFhirPath();
    List<Practitioner> practitioners = iFhirPath.evaluate(res,
        "entry.resource.ofType(Practitioner)", Practitioner.class);
    System.out.println(practitioners.get(0).getNameFirstRep().getFamily());

    return new MethodOutcome();
  }

}
