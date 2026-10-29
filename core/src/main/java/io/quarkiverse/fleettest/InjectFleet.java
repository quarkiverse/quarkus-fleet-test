package io.quarkiverse.fleettest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link Fleet} field of a {@link QuarkusFleetTest} class for injection. A {@link Fleet} test method
 * parameter is resolved without any annotation.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InjectFleet {
}
