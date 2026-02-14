package com.refiq.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RefIqPlatformApplication {

  public static void main(String[] args) {
    SpringApplication.run(RefIqPlatformApplication.class, args);
  }


  // TODO

  // LOGGING estructurado para CSvNormalizer y el nuevo servicio
  //         repasar que no haya magic strings en la codebase

  // TESTING

  // Revisar ultima versión de INgestionINtegrationTest, csvNormalizrTest, IngestionServiceTest y
  // CalculationServiceIntegrationTest (All green)
  // MOdificar el CalculationServiceTest debido a que ahora devolvemos JSON al front, no string (DONE)

  // Plantearse si normalizar todas las estrategias de test a Arrange Act Assert o Given When Then

}
