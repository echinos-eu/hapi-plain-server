package example.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.validation.FhirValidator;
import java.io.IOException;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.NpmPackageValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.springframework.stereotype.Service;

@Service
public class EAUValidator extends FhirValidator {

  public EAUValidator(FhirContext ctx) throws IOException {
    super(ctx);

    NpmPackageValidationSupport npmPackageValidationSupport = new NpmPackageValidationSupport(ctx);
    npmPackageValidationSupport.loadPackageFromClasspath("classpath:/packages/de.basisprofil.r4-1.5.2.tgz");
    npmPackageValidationSupport.loadPackageFromClasspath("classpath:/packages/kbv.basis-1.7.0.tgz");
    npmPackageValidationSupport.loadPackageFromClasspath("classpath:/packages/kbv.ita.eau-1.2.0.tgz");
    npmPackageValidationSupport.loadPackageFromClasspath("classpath:/packages/kbv.ita.for-1.2.0.tgz");

    ValidationSupportChain validationSupportChain = new ValidationSupportChain(
        npmPackageValidationSupport,
        new DefaultProfileValidationSupport(ctx),
        new CommonCodeSystemsTerminologyService(ctx),
        new InMemoryTerminologyServerValidationSupport(ctx),
        new SnapshotGeneratingValidationSupport(ctx));
    FhirInstanceValidator validator = new FhirInstanceValidator(validationSupportChain);
    registerValidatorModule(validator);

    validator.setAnyExtensionsAllowed(false);
    validator.setNoExtensibleWarnings(true);
    validator.setErrorForUnknownProfiles(false);
  }

}
