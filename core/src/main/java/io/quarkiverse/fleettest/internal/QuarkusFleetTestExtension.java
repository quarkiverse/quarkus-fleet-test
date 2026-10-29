package io.quarkiverse.fleettest.internal;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.AnnotationSupport;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.FleetInstance;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkus.runtime.test.TestHttpEndpointProvider;
import io.quarkus.test.common.RestAssuredStateManager;
import io.quarkus.test.common.http.TestHTTPResourceManager;
import io.quarkus.test.config.ConfigInjector;
import io.quarkus.test.config.ThreadLocalConfigSourceProvider;
import io.quarkus.test.config.ValueRegistryInjector;
import io.quarkus.test.junit.QuarkusTestExtension;
import io.quarkus.value.registry.ValueRegistry;
import io.smallrye.config.Config;

public class QuarkusFleetTestExtension
        implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace
            .create(QuarkusFleetTestExtension.class);
    private static final int LOG_TAIL_LINES = 30;
    private static final List<Function<Class<?>, String>> TEST_HTTP_ENDPOINT_PROVIDERS = TestHttpEndpointProvider.load();

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        Class<?> testClass = context.getRequiredTestClass();
        QuarkusFleetTest annotation = testClass.getAnnotation(QuarkusFleetTest.class);
        if (annotation == null) {
            throw new IllegalStateException(testClass.getName() + " is not annotated with @QuarkusFleetTest");
        }
        if (annotation.instances() < 1) {
            throw new IllegalArgumentException("@QuarkusFleetTest(instances) must be at least 1 on " + testClass.getName());
        }
        int running = annotation.running() < 0 ? annotation.instances() : annotation.running();
        if (running < 1 || running > annotation.instances()) {
            throw new IllegalArgumentException("@QuarkusFleetTest(running) must be between 1 and instances on "
                    + testClass.getName());
        }
        // validated here, before anything is started on their behalf
        ReadinessSettings readiness = ReadinessSettings.from(annotation.readiness());
        FleetState state = FleetState.start(context, annotation.instances(), running, readiness);
        context.getStore(NAMESPACE).put(FleetState.class, state);
        ThreadLocalConfigSourceProvider.set(ConfigInjector.get(context));
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        FleetState state = state(context);
        Object testInstance = context.getRequiredTestInstance();
        ValueRegistry valueRegistry = ValueRegistryInjector.get(context);
        Config config = ConfigInjector.get(context);

        ValueRegistryInjector.inject(testInstance, valueRegistry);
        ConfigInjector.inject(testInstance, config);
        ThreadLocalConfigSourceProvider.set(config);
        TestHTTPResourceManager.inject(testInstance, valueRegistry, config);
        state.testResourceManager().inject(valueRegistry, testInstance);
        RestAssuredStateManager.setTestUri(valueRegistry,
                QuarkusTestExtension.getEndpointPath(context, TEST_HTTP_ENDPOINT_PROVIDERS));
        injectFleet(testInstance, state.fleet());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        ThreadLocalConfigSourceProvider.reset();
        RestAssuredStateManager.clearState();
        FleetState state = state(context);
        if (context.getExecutionException().isPresent()) {
            // what the fleet looked like when the test failed, so that the failure can be read without re-running
            String report = fleetStateReport(context, state.fleet());
            System.err.print(report);
            writeReport(context, state, report);
        }
        // a test that pinned the load balancer must not leak that into the next one
        state.fleet().reset();
    }

    private static String fleetStateReport(ExtensionContext context, DefaultFleet fleet) {
        StringBuilder sb = new StringBuilder();
        sb.append("Fleet state when ").append(context.getDisplayName()).append(" failed:\n");
        sb.append(fleet.describe());
        if (!fleet.timeline().isEmpty()) {
            // what the balancer did during the test, timestamped to line up with the logs below
            sb.append("  what the load balancer did during the test:\n");
            sb.append(fleet.timeline().describe());
        }
        for (FleetInstance instance : fleet.instances()) {
            appendInstanceLog(sb, instance);
        }
        return sb.toString();
    }

    private static void appendInstanceLog(StringBuilder sb, FleetInstance instance) {
        Path logFile = instance.logFile();
        if (logFile == null || !Files.exists(logFile)) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(logFile);
        } catch (IOException ignored) {
            // the path was printed in the snapshot above
            return;
        }
        int from = Math.max(0, lines.size() - LOG_TAIL_LINES);
        // warnings and errors from before the tail are the ones most likely to explain the failure, and the ones
        // a fixed-length tail of a chatty log buries; surface them before the tail.
        List<String> notableBeforeTail = new ArrayList<>();
        for (int i = 0; i < from; i++) {
            if (isNotable(lines.get(i))) {
                notableBeforeTail.add(lines.get(i));
            }
        }
        if (!notableBeforeTail.isEmpty()) {
            sb.append("  warnings and errors earlier in instance ").append(instance.index()).append("'s log:\n");
            for (String line : notableBeforeTail) {
                sb.append("    ").append(line).append('\n');
            }
        }
        sb.append("  last ").append(lines.size() - from).append(" line(s) of instance ").append(instance.index())
                .append(" (").append(logFile).append("):\n");
        for (String line : lines.subList(from, lines.size())) {
            sb.append("    ").append(line).append('\n');
        }
    }

    private static boolean isNotable(String line) {
        return line.contains(" WARN ") || line.contains(" ERROR ") || line.contains(" SEVERE ")
                || line.contains("Exception");
    }

    // The same report, written to a file under the build directory, so it survives in CI where the console is
    // not kept. Best effort: a failure to write never masks the test's own failure.
    private static void writeReport(ExtensionContext context, FleetState state, String report) {
        try {
            Path dir = state.buildOutputDirectory().resolve("fleet-test");
            Files.createDirectories(dir);
            String method = context.getDisplayName().replaceAll("[^a-zA-Z0-9._-]+", "_").replaceAll("^_+|_+$", "");
            String name = context.getRequiredTestClass().getName() + "." + method + "-fleet.txt";
            Files.writeString(dir.resolve(name), report);
        } catch (Exception ignored) {
            // the report was already printed to stderr
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == Fleet.class
                || ValueRegistryInjector.PARAMETER_RESOLVER.supportsParameter(parameterContext, extensionContext)
                || ConfigInjector.PARAMETER_RESOLVER.supportsParameter(parameterContext, extensionContext);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        if (parameterContext.getParameter().getType() == Fleet.class) {
            return state(extensionContext).fleet();
        }
        if (ValueRegistryInjector.PARAMETER_RESOLVER.supportsParameter(parameterContext, extensionContext)) {
            return ValueRegistryInjector.PARAMETER_RESOLVER.resolveParameter(parameterContext, extensionContext);
        }
        return ConfigInjector.PARAMETER_RESOLVER.resolveParameter(parameterContext, extensionContext);
    }

    private static FleetState state(ExtensionContext context) {
        FleetState state = context.getStore(NAMESPACE).get(FleetState.class, FleetState.class);
        if (state == null) {
            throw new IllegalStateException("The fleet was not started; is the test class annotated with @QuarkusFleetTest?");
        }
        return state;
    }

    private static void injectFleet(Object testInstance, Fleet fleet) throws IllegalAccessException {
        for (Field field : AnnotationSupport.findAnnotatedFields(testInstance.getClass(), InjectFleet.class)) {
            if (field.getType() != Fleet.class) {
                throw new IllegalStateException("@InjectFleet field " + field.getDeclaringClass().getName() + "."
                        + field.getName() + " must be of type " + Fleet.class.getName());
            }
            field.setAccessible(true);
            field.set(testInstance, fleet);
        }
    }
}
