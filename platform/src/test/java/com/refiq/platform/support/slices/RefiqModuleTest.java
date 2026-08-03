package com.refiq.platform.support.slices;


import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-anotación corporativa para tests de integración modulares en RefIQ. Define las reglas de
 * aislamiento estándar de la plataforma.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ActiveProfiles("test")
@ApplicationModuleTest(extraIncludes = "shared")
public @interface RefiqModuleTest {

}
















