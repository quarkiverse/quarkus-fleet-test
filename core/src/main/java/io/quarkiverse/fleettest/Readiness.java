package io.quarkiverse.fleettest;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * How the load balancer decides which instances receive new connections, modelled on what a kubelet and
 * kube-proxy do with a readiness probe. Off by default: every running instance is routed to.
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface Readiness {

    /**
     * The path the load balancer polls on each instance, for example {@code /q/health/ready}. A 2xx answer is a
     * success, anything else (including no answer within the interval) a failure. Empty disables readiness.
     */
    String path() default "";

    /**
     * How often each instance is polled. A kubelet defaults to 10 s; a test wants far less.
     */
    long intervalMillis() default 250;

    /**
     * Consecutive failures before an instance is considered unready. A kubelet defaults to 3.
     */
    int failureThreshold() default 3;

    /**
     * Consecutive successes before an unready instance is considered ready again. A kubelet defaults to 1.
     */
    int successThreshold() default 1;

    /**
     * How long the load balancer keeps routing to an instance after it became unready or was stopped. This is the
     * propagation delay between an endpoint change and every proxy acting on it, which is what
     * {@code quarkus.shutdown.delay} exists for. Zero means immediate.
     */
    long deregistrationLagMillis() default 0;

    /**
     * What to do when no instance is ready: {@code false} refuses connections, as kube-proxy does for a Service
     * without ready endpoints; {@code true} routes to every running instance anyway, as an AWS ALB or Envoy in
     * panic mode does.
     */
    boolean failOpen() default false;
}
