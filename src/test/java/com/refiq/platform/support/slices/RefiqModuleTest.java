package com.refiq.platform.support.slices;

import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom meta-annotation for modular integration tests in the RefIQ platform.
 * <p>
 * Standardizes isolation rules by implicitly activating the "test" profile and
 * ensuring the "shared" module is always loaded alongside the module under test,
 * providing required cross-cutting configurations (e.g., exception handlers, thread pools).
 * </p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ActiveProfiles("test")
@ApplicationModuleTest(extraIncludes = "shared")
public @interface RefiqModuleTest {

}