package org.acme.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;

/**
 * The bug: a long-running job runs in a thread of the instance that accepted it, and the thread dies with the
 * process (OOM-killed, node failure, scale-in). The other instance never knew about the job, so nothing resumes
 * it: the import stays where it was for ever, and the client who was told it had started waits for nothing.
 */
@QuarkusFleetTest(instances = 2)
class ImportLostWhenInstanceDiesIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void theImportStopsWhereTheKilledInstanceLeftIt() {
        int id = Imports.startOnAnInstanceAndKillIt(fleet);
        long snapshot = Imports.rows(id);
        assertThat(snapshot).isLessThan(Imports.ROWS);

        // and it does not move: nobody is working on it any more
        await().during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(5))
                .until(() -> Imports.rows(id) == snapshot);
    }
}
