package io.quarkiverse.fleettest.internal;

import static io.quarkus.runtime.configuration.ConfigSourceOrdinal.INTEGRATION_TEST;
import static io.quarkus.test.common.ListeningResult.SERVER_LISTENING_RESULT;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.extension.ExtensionContext;

import io.quarkus.deployment.dev.testing.TestConfig;
import io.quarkus.deployment.dev.testing.TestConfigCustomizer;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.ValueRegistryConfigSource;
import io.quarkus.runtime.ValueRegistryImpl;
import io.quarkus.runtime.configuration.ConfigUtils;
import io.quarkus.runtime.logging.LoggingSetupRecorder;
import io.quarkus.test.common.ArtifactLauncher;
import io.quarkus.test.common.ListeningAddress;
import io.quarkus.test.common.ListeningResult;
import io.quarkus.test.common.TestResourceManager;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.config.ConfigInjector;
import io.quarkus.test.config.ThreadLocalConfigSourceProvider;
import io.quarkus.test.config.ValueRegistryInjector;
import io.quarkus.test.junit.ArtifactTypeUtil;
import io.quarkus.test.junit.IntegrationTestUtil;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.TestProfileAndProperties;
import io.quarkus.test.junit.launcher.ArtifactLauncherProvider;
import io.quarkus.value.registry.ValueRegistry;
import io.smallrye.config.Config;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

/**
 * Everything started for one test class: Dev Services, test resources, the instances and the load balancer.
 * Stored in the class-level JUnit store, so JUnit closes it when the class is done. Each test class gets its own
 * fleet, so classes with different {@code @TestProfile}s never share one.
 * <p>
 * The sequence mirrors {@code QuarkusIntegrationTestExtension.doProcessStart}. The launchers come from the same
 * {@link ArtifactLauncherProvider}s, so jar, container and native artifacts are launched exactly as
 * {@code @QuarkusIntegrationTest} launches them; what differs is that N launchers are created instead of one, that
 * every one of them is told to use port 0, and that the address registered for the test is the load balancer's.
 */
final class FleetState implements AutoCloseable {

    private final ExtensionContext context;
    private final ArtifactLauncher.InitContext.DevServicesLaunchResult devServices;
    private final TestResourceManager testResourceManager;
    private final DefaultFleet fleet;
    private final Path buildOutputDirectory;

    private FleetState(ExtensionContext context, ArtifactLauncher.InitContext.DevServicesLaunchResult devServices,
            TestResourceManager testResourceManager, DefaultFleet fleet, Path buildOutputDirectory) {
        this.context = context;
        this.devServices = devServices;
        this.testResourceManager = testResourceManager;
        this.fleet = fleet;
        this.buildOutputDirectory = buildOutputDirectory;
    }

    // Where the build writes, i.e. target/ under Maven: a failure report is written here so CI keeps it.
    Path buildOutputDirectory() {
        return buildOutputDirectory;
    }

    DefaultFleet fleet() {
        return fleet;
    }

    TestResourceManager testResourceManager() {
        return testResourceManager;
    }

    static FleetState start(ExtensionContext context, int instanceCount, int running, ReadinessSettings readiness)
            throws Exception {
        Class<?> testClass = context.getRequiredTestClass();
        Properties artifactProperties = IntegrationTestUtil.readQuarkusArtifactProperties(context);
        String artifactType = artifactProperties.getProperty("type");
        Path buildOutputDirectory = buildOutputDirectory(testClass);

        Config config = Config.get();
        TestConfig testConfig = config.getConfigMapping(TestConfig.class);
        String integrationTestProfile = testConfig.integrationTestProfile();
        boolean containerLaunch = ArtifactTypeUtil.isContainer(artifactType)
                || (ArtifactTypeUtil.isJar(artifactType) && "test-with-native-agent".equals(integrationTestProfile));

        Class<? extends QuarkusTestProfile> profileClass = findProfile(testClass);
        TestProfileAndProperties testProfileAndProperties = TestProfileAndProperties.ofNullable(profileClass,
                LaunchMode.NORMAL);

        ArtifactLauncher.InitContext.DevServicesLaunchResult devServices = FleetDevServices.start(testClass,
                testProfileAndProperties, containerLaunch);
        TestResourceManager testResourceManager = null;
        DefaultFleet fleet = null;
        try {
            testResourceManager = new TestResourceManager(testClass, profileClass,
                    testResourcesOf(testProfileAndProperties),
                    testProfileAndProperties.isDisabledGlobalTestResources(), devServices.properties(),
                    Optional.ofNullable(devServices.networkId()));
            testResourceManager.init(testProfileAndProperties.testProfileClassName().orElse(null));

            Map<String, String> additionalProperties = new HashMap<>();
            // propagate Quarkus properties set from the build tool, as @QuarkusIntegrationTest does
            Properties existingSysProps = System.getProperties();
            for (String name : existingSysProps.stringPropertyNames()) {
                if (name.startsWith("quarkus.") && !name.equals("quarkus.profile") && !name.equals("quarkus.test.arg-line")) {
                    additionalProperties.put(name, existingSysProps.getProperty(name));
                }
            }
            additionalProperties.putAll(devServices.properties());
            additionalProperties.putAll(testResourceManager.start());
            // includes the config profile of the @TestProfile, if any, as quarkus.profile
            additionalProperties.putAll(testProfileAndProperties.properties());

            ConfigSource integrationTestSource = new PropertiesConfigSource(additionalProperties,
                    INTEGRATION_TEST.getName(), INTEGRATION_TEST.getOrdinal());
            ValueRegistry valueRegistry = ValueRegistryImpl.builder().addDiscoveredInfos()
                    .withRuntimeSource(new SmallRyeConfigBuilder().withSources(integrationTestSource).build())
                    .withRuntimeSource(config)
                    .build();
            ValueRegistryInjector.set(context, valueRegistry);
            SmallRyeConfig testJvmConfig = ConfigUtils.configBuilder(LaunchMode.TEST)
                    .forClassLoader(Config.get().getClass().getClassLoader())
                    .withCustomizers(new TestConfigCustomizer(LaunchMode.TEST))
                    .withCustomizers(ValueRegistryConfigSource.customizer(valueRegistry))
                    .withSources(integrationTestSource)
                    .build();
            ConfigInjector.set(context, testJvmConfig);
            ThreadLocalConfigSourceProvider.set(testJvmConfig);

            // The launcher providers read the port to use from Config.get(). They get a config of their own, in
            // which it is 0 so that each instance takes a free port, which the launchers read back from the
            // instance's log. It must not see the value registry: once the fleet is up, that registers the load
            // balancer's port under the same key, and an instance restarted during a test would try to take it.
            SmallRyeConfig launcherConfig = ConfigUtils.configBuilder(LaunchMode.TEST)
                    .forClassLoader(Config.get().getClass().getClassLoader())
                    .withCustomizers(new TestConfigCustomizer(LaunchMode.TEST))
                    .withSources(integrationTestSource, new PropertiesConfigSource(
                            Map.of("quarkus.http.test-port", "0", "quarkus.http.test-ssl-port", "0"),
                            "quarkus-fleet-test", Integer.MAX_VALUE))
                    .build();

            // sets up logging in the test JVM, otherwise log records stay buffered
            LoggingSetupRecorder.handleFailedStart();

            ArtifactLauncherProvider launcherProvider = launcherProvider(artifactType, integrationTestProfile);
            ArtifactLauncherProvider.CreateContext createContext = new FleetCreateContext(artifactProperties,
                    buildOutputDirectory, testClass, profileClass, devServices);
            Supplier<ArtifactLauncher<?>> launchers = () -> {
                ThreadLocalConfigSourceProvider.set(launcherConfig);
                try {
                    return launcherProvider.create(createContext);
                } finally {
                    ThreadLocalConfigSourceProvider.set(testJvmConfig);
                }
            };

            List<DefaultFleetInstance> instances = new ArrayList<>(instanceCount);
            for (int i = 0; i < instanceCount; i++) {
                instances.add(new DefaultFleetInstance(i, launchers, additionalProperties, testConfig.waitTime(),
                        containerLaunch));
            }
            fleet = new DefaultFleet(instances, readiness);
            // one after the other: instance 0 is up before the others start, so that whatever runs once at startup
            // against the shared Dev Services (schema generation, migrations) does not race
            for (int i = 0; i < running; i++) {
                instances.get(i).start();
            }

            // the test talks to the load balancer: this is what RestAssured and @TestHTTPResource resolve to
            ListeningAddress loadBalancerAddress = new ListeningAddress(fleet.port(), "http");
            loadBalancerAddress.register(valueRegistry, testJvmConfig);
            valueRegistry.register(SERVER_LISTENING_RESULT,
                    Optional.of(new ListeningResult(loadBalancerAddress, instances.get(0).logFile())));

            return new FleetState(context, devServices, testResourceManager, fleet, buildOutputDirectory);
        } catch (Throwable e) {
            Closeables.closeAll(e, fleet, testResourceManager, devServices);
            throw e;
        }
    }

    @Override
    public void close() {
        try {
            Closeables.closeAll(null, fleet, testResourceManager, devServices);
        } finally {
            ConfigInjector.clear(context);
            ValueRegistryInjector.clear(context);
        }
    }

    private static ArtifactLauncherProvider launcherProvider(String artifactType, String integrationTestProfile) {
        for (ArtifactLauncherProvider provider : ServiceLoader.load(ArtifactLauncherProvider.class)) {
            if (provider.supportsArtifactType(artifactType, integrationTestProfile)) {
                return provider;
            }
        }
        throw new IllegalStateException("Artifact type '" + artifactType + "' is not supported by @QuarkusFleetTest");
    }

    private static Class<? extends QuarkusTestProfile> findProfile(Class<?> testClass) {
        for (Class<?> c = testClass; c != Object.class; c = c.getSuperclass()) {
            TestProfile annotation = c.getAnnotation(TestProfile.class);
            if (annotation != null) {
                return annotation.value();
            }
        }
        return null;
    }

    // The test resources a QuarkusTestProfile declares, in the form TestResourceManager takes.
    private static List<TestResourceManager.TestResourceClassEntry> testResourcesOf(
            TestProfileAndProperties testProfileAndProperties) {
        List<TestResourceManager.TestResourceClassEntry> entries = new ArrayList<>();
        testProfileAndProperties.testProfile().ifPresent(profile -> {
            for (QuarkusTestProfile.TestResourceEntry entry : profile.testResources()) {
                entries.add(new TestResourceManager.TestResourceClassEntry(entry.getClazz(), entry.getArgs(), null,
                        entry.isParallel(), TestResourceScope.MATCHING_RESOURCES));
            }
        });
        return entries;
    }

    /**
     * Where {@code quarkus-artifact.properties} and the artifact are: the Maven/Gradle build output directory.
     * TODO a copy of the simple cases of {@code IntegrationTestUtil.determineBuildOutputDirectory}, which is
     * package-private.
     */
    private static Path buildOutputDirectory(Class<?> testClass) {
        String configured = System.getProperty("build.output.directory");
        if (configured != null) {
            return Paths.get(configured);
        }
        CodeSource codeSource = testClass.getProtectionDomain().getCodeSource();
        if (codeSource != null) {
            URL location = codeSource.getLocation();
            if (location != null && "file".equals(location.getProtocol())) {
                // target/test-classes (Maven) or build/classes/java/test (Gradle)
                Path candidate = Paths.get(location.getPath().replaceAll("/$", ""));
                for (int up = 0; up < 3 && candidate != null; up++) {
                    candidate = candidate.getParent();
                    if (candidate != null && Files.exists(candidate.resolve("quarkus-artifact.properties"))) {
                        return candidate;
                    }
                }
            }
        }
        throw new IllegalStateException("Unable to determine the build output directory for " + testClass.getName()
                + "; set the 'build.output.directory' system property");
    }

    private record FleetCreateContext(Properties quarkusArtifactProperties, Path buildOutputDirectory,
            Class<?> testClass, Class<? extends QuarkusTestProfile> profile,
            ArtifactLauncher.InitContext.DevServicesLaunchResult devServicesLaunchResult)
            implements
                ArtifactLauncherProvider.CreateContext {
    }
}
