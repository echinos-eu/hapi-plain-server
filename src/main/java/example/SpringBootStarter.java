package example;

import java.util.Locale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

@ServletComponentScan
@SpringBootApplication
public class SpringBootStarter {

  public static void main(String[] args) {
    SpringApplication.run(SpringBootStarter.class, args);
    Locale.setDefault(Locale.ENGLISH);
  }
}
