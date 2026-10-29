package io.quarkiverse.fleettest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.fleettest.internal.QuarkusFleetTestExtension;

/**
 * Runs the packaged application several times, as separate processes, behind a TCP load balancer, and lets the
 * test talk to the fleet (through the load balancer, which is what RestAssured and {@code @TestHTTPResource}
 * point at) or to one instance.
 * <p>
 * Like {@code @QuarkusIntegrationTest}, the test runs against the artifact produced by the build, so it has to
 * run after {@code mvn package} (the failsafe plugin does that). Dev Services are started once in the test JVM
 * and their configuration is passed to every instance, so all instances share the same database, broker, etc.
 * Each test class gets a fleet of its own.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ExtendWith(QuarkusFleetTestExtension.class)
public @interface QuarkusFleetTest {

    /**
     * How many instances of the application to start.
     */
    int instances() default 2;

    /**
     * How many of them to start before the tests, from instance 0 up. The rest are created but not started,
     * for a test to {@code start()} when it wants to see a replica join: a scale-out, a consumer group
     * rebalance, a node joining a cluster. By default, all of them.
     */
    int running() default -1;

    /**
     * How the load balancer uses the instances' readiness. Off by default.
     */
    Readiness readiness() default @Readiness;
}
