package example.config;

import ca.uhn.fhir.context.FhirContext;
import example.HapiServer;
import example.provider.PatientProvider;
import example.provider.ValidationProvider;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HapiServerConfig {

  @Bean
  public ServletRegistrationBean<HapiServer> hapiServlet(FhirContext fhirContext, PatientProvider patientProvider, ValidationProvider validationProvider) {
    HapiServer server = new HapiServer(fhirContext, patientProvider, validationProvider);
    return new ServletRegistrationBean<>(server, "/*");
  }

  @Bean
  public FhirContext fhirContext() {
    return FhirContext.forR4();
  }
}
