package io.quarkiverse.fleettest.internal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import io.quarkus.test.common.ArtifactLauncher;
import io.quarkus.test.common.ListeningAddress;
import io.quarkus.test.common.ListeningResult;
import io.quarkus.test.common.ListeningResults;

// A launcher that starts nothing: it answers start() as told, and remembers whether it was closed.
final class FakeLauncher implements ArtifactLauncher<ArtifactLauncher.InitContext> {

    private final Supplier<ListeningResults> start;
    private final IOException closeFailure;
    volatile Map<String, String> systemProperties;
    volatile boolean closed;

    FakeLauncher(Supplier<ListeningResults> start, IOException closeFailure) {
        this.start = start;
        this.closeFailure = closeFailure;
    }

    static FakeLauncher listeningOn(int port) {
        return new FakeLauncher(() -> listening(port), null);
    }

    static ListeningResults listening(int port) {
        return new ListeningResults(
                Optional.of(new ListeningResult(new ListeningAddress(port, "http"), Path.of("fake.log"))),
                Optional.empty());
    }

    @Override
    public void init(InitContext initContext) {
    }

    @Override
    public ListeningResults start() throws IOException {
        return start.get();
    }

    @Override
    public LaunchResult runToCompletion(String[] args) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void includeAsSysProps(Map<String, String> systemProperties) {
        this.systemProperties = systemProperties;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        if (closeFailure != null) {
            throw closeFailure;
        }
    }
}
