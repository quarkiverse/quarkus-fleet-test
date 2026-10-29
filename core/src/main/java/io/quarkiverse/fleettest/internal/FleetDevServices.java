package io.quarkiverse.fleettest.internal;

import static io.quarkus.runtime.configuration.QuarkusConfigBuilderCustomizer.QUARKUS_PROFILE;
import static io.quarkus.test.common.PathTestHelper.getAppClassLocationForTestLocation;
import static io.quarkus.test.common.PathTestHelper.getTestClassesLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.jandex.Index;

import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.bootstrap.app.AugmentAction;
import io.quarkus.bootstrap.app.CuratedApplication;
import io.quarkus.bootstrap.app.QuarkusBootstrap;
import io.quarkus.bootstrap.model.ApplicationModel;
import io.quarkus.bootstrap.utils.BuildToolHelper;
import io.quarkus.bootstrap.workspace.ArtifactSources;
import io.quarkus.bootstrap.workspace.SourceDir;
import io.quarkus.deployment.builditem.DevServicesAdditionalConfigBuildItem;
import io.quarkus.deployment.builditem.DevServicesCustomizerBuildItem;
import io.quarkus.deployment.builditem.DevServicesLauncherConfigResultBuildItem;
import io.quarkus.deployment.builditem.DevServicesNetworkIdBuildItem;
import io.quarkus.deployment.builditem.DevServicesRegistryBuildItem;
import io.quarkus.deployment.builditem.DevServicesSharedNetworkBuildItem;
import io.quarkus.deployment.util.ContainerRuntimeUtil;
import io.quarkus.paths.PathList;
import io.quarkus.test.common.ArtifactLauncher;
import io.quarkus.test.common.PathTestHelper;
import io.quarkus.test.common.TestClassIndexer;
import io.quarkus.test.junit.NativeDevServicesHandler;
import io.quarkus.test.junit.TestProfileAndProperties;
import io.smallrye.common.process.ProcessBuilder;
import io.smallrye.config.Config;

/**
 * Starts the Dev Services once, in the test JVM, by running a build of the application in test mode, and collects
 * the configuration they produce. That configuration is then passed to every instance of the fleet.
 * <p>
 * TODO this is a copy of {@code io.quarkus.test.junit.IntegrationTestUtil.handleDevServices}, which is
 * package-private. Replace with the upstream method once it is public.
 */
final class FleetDevServices {

    private FleetDevServices() {
    }

    /**
     * @param containerLaunch whether the instances run as containers; the Dev Services then join a network the
     *        instances join as well, and announce themselves with addresses valid on that network
     */
    static ArtifactLauncher.InitContext.DevServicesLaunchResult start(Class<?> requiredTestClass,
            TestProfileAndProperties testProfileAndProperties, boolean containerLaunch) throws Exception {
        Path testClassLocation = getTestClassesLocation(requiredTestClass);
        final Path appClassLocation = getAppClassLocationForTestLocation(testClassLocation);

        final PathList.Builder rootBuilder = PathList.builder();

        if (!appClassLocation.equals(testClassLocation)) {
            rootBuilder.add(testClassLocation);
            // if test classes is a dir, we should also check whether test resources dir exists as a separate dir (gradle)
            final Path testResourcesLocation = PathTestHelper.getResourcesForClassesDirOrNull(testClassLocation, "test");
            if (testResourcesLocation != null) {
                rootBuilder.add(testResourcesLocation);
            }
        }
        final QuarkusBootstrap.Builder runnerBuilder = QuarkusBootstrap.builder()
                .setIsolateDeployment(true)
                .setMode(QuarkusBootstrap.Mode.TEST);

        final Path projectRoot = Paths.get("").normalize().toAbsolutePath();
        runnerBuilder.setProjectRoot(projectRoot);
        runnerBuilder.setTargetDirectory(PathTestHelper.getProjectBuildDir(projectRoot, testClassLocation));

        if (Files.exists(appClassLocation)) {
            rootBuilder.add(appClassLocation);
        }
        final Path appResourcesLocation = PathTestHelper.getResourcesForClassesDirOrNull(appClassLocation, "main");
        if (appResourcesLocation != null) {
            if (Files.exists(appResourcesLocation)) {
                rootBuilder.add(appResourcesLocation);
            }
        }

        // If gradle project running directly with IDE
        if (System.getProperty(BootstrapConstants.SERIALIZED_TEST_APP_MODEL) == null) {
            ApplicationModel model = BuildToolHelper.enableGradleAppModelForTest(projectRoot);
            if (model != null && model.getApplicationModule() != null) {
                final ArtifactSources testSources = model.getApplicationModule().getTestSources();
                if (testSources != null) {
                    for (SourceDir src : testSources.getSourceDirs()) {
                        if (!Files.exists(src.getOutputDir())) {
                            final Path classes = src.getOutputDir();
                            if (!rootBuilder.contains(classes)) {
                                rootBuilder.add(classes);
                            }
                        }
                    }
                }
                for (SourceDir src : model.getApplicationModule().getMainSources().getSourceDirs()) {
                    if (!Files.exists(src.getOutputDir())) {
                        final Path classes = src.getOutputDir();
                        if (!rootBuilder.contains(classes)) {
                            rootBuilder.add(classes);
                        }
                    }
                }
            }
        } else if (System.getProperty(BootstrapConstants.OUTPUT_SOURCES_DIR) != null) {
            final String[] sourceDirectories = System.getProperty(BootstrapConstants.OUTPUT_SOURCES_DIR).split(",");
            for (String sourceDirectory : sourceDirectories) {
                final Path directory = Paths.get(sourceDirectory);
                if (Files.exists(directory) && !rootBuilder.contains(directory)) {
                    rootBuilder.add(directory);
                }
            }
        }
        runnerBuilder.setApplicationRoot(rootBuilder.build());

        // Set the config profile and properties overrides from the @TestProfile also for DevServices augmentation
        Properties properties = new Properties();
        // Ensure that these properties cannot be overridden
        properties.put(ConfigSource.CONFIG_ORDINAL, Integer.MAX_VALUE);
        testProfileAndProperties.configProfile().ifPresent(configProfile -> properties.put(QUARKUS_PROFILE, configProfile));
        properties.putAll(testProfileAndProperties.properties());
        CuratedApplication curatedApplication = runnerBuilder
                .setTest(true)
                .setBuildSystemProperties(properties)
                .build()
                .bootstrap();

        Index testClassesIndex = TestClassIndexer.indexTestClasses(requiredTestClass);
        // we need to write the Index to make it reusable from other parts of the testing infrastructure that run in different ClassLoaders
        TestClassIndexer.writeIndex(testClassesIndex, requiredTestClass);

        Map<String, String> propertyMap = new HashMap<>();
        AugmentAction augmentAction;
        if (containerLaunch) {
            // when the application is going to be launched as a docker container, we need to make containers started by DevServices
            // use a shared network that the application container can then use as well
            augmentAction = curatedApplication.createAugmentor(
                    "io.quarkus.deployment.builditem.DevServicesSharedNetworkBuildItem$Factory",
                    Map.of(DevServicesSharedNetworkBuildItem.SOURCE_PROPERTY, "io.quarkiverse.fleettest"));
        } else {
            augmentAction = curatedApplication.createAugmentor();
        }
        augmentAction.performCustomBuild(NativeDevServicesHandler.class.getName(), new BiConsumer<String, String>() {
            @Override
            public void accept(String s, String s2) {
                propertyMap.put(s, s2);
            }
        }, DevServicesLauncherConfigResultBuildItem.class.getName(), DevServicesNetworkIdBuildItem.class.getName(),
                DevServicesRegistryBuildItem.class.getName(), DevServicesCustomizerBuildItem.class.getName(),
                DevServicesAdditionalConfigBuildItem.class.getName());

        String networkId = propertyMap.get("quarkus.test.container.network");
        boolean manageNetwork = false;
        if (containerLaunch && networkId == null) {
            // use the network the user has specified or else just generate one if none is configured
            Optional<String> configured = Config.get().getOptionalValue("quarkus.test.container.network", String.class);
            if (configured.isPresent()) {
                networkId = configured.get();
            } else {
                networkId = "quarkus-fleet-test-" + UUID.randomUUID().toString().substring(0, 5);
                manageNetwork = true;
            }
        }
        LaunchResult result = new LaunchResult(propertyMap, networkId, manageNetwork, curatedApplication);
        createNetworkIfNecessary(result);
        return result;
    }

    // the network has to exist before user code (test resources) is aware of it
    private static void createNetworkIfNecessary(LaunchResult result) {
        if (!result.manageNetwork() || result.networkId() == null) {
            return;
        }
        ContainerRuntimeUtil.ContainerRuntime containerRuntime = ContainerRuntimeUtil.detectContainerRuntime(true);
        try {
            ProcessBuilder.exec(containerRuntime.getExecutableName(), "network", "create", result.networkId());
            // the cleanup is a shutdown hook because other services (test resources) may still be on the network
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    ProcessBuilder.exec(containerRuntime.getExecutableName(), "network", "rm", result.networkId());
                } catch (Exception e) {
                    System.out.printf("Unable to delete container network '%s'%n", result.networkId());
                }
            }));
        } catch (Exception e) {
            throw new RuntimeException("Creating container network '%s' completed unsuccessfully"
                    .formatted(result.networkId()), e);
        }
    }

    private record LaunchResult(Map<String, String> properties, String networkId, boolean manageNetwork,
            CuratedApplication curatedApplication) implements ArtifactLauncher.InitContext.DevServicesLaunchResult {

        @Override
        public CuratedApplication getCuratedApplication() {
            return curatedApplication;
        }

        @Override
        public void close() {
            curatedApplication.close();
        }
    }
}
