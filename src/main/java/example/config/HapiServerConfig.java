package example.config;

import ca.uhn.fhir.context.FhirContext;
import example.HapiServer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HapiServerConfig {

  @Bean
  public ServletRegistrationBean<HapiServer> hapiServlet(FhirContext fhirContext) {
    HapiServer server = new HapiServer(fhirContext);
    return new ServletRegistrationBean<>(server, "/*");
  }

  @Bean
  public FhirContext fhirContext() {
    return FhirContext.forR4();
  }
}
