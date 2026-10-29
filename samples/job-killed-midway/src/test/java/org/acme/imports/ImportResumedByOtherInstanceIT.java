package org.acme.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.quarkiverse.fleettest.Fleet;
import io.quarkiverse.fleettest.InjectFleet;
import io.quarkiverse.fleettest.QuarkusFleetTest;
import io.quarkus.test.junit.TestProfile;

/**
 * The fix: a checkpoint and a reaper. The job writes its progress and a heartbeat to the database after every
 * row, and every instance periodically claims jobs whose heartbeat went stale and resumes them from the
 * checkpoint. The import finishes on the survivor, with no row done twice.
 */
@QuarkusFleetTest(instances = 2)
@TestProfile(ResumableProfile.class)
class ImportResumedByOtherInstanceIT {

    @InjectFleet
    Fleet fleet;

    @Test
    void theSurvivorFinishesTheImportFromTheCheckpoint() {
        int id = Imports.startOnAnInstanceAndKillIt(fleet);

        // the heartbeat goes stale, the survivor's reaper claims the import and runs the rest of it
        await().atMost(Duration.ofSeconds(30)).until(() -> Imports.processed(id) == Imports.ROWS);

        // every row exactly once: the primary key on (import_id, row_no) would have rejected a row done twice,
        // so a complete count means none was
        assertThat(Imports.rows(id)).isEqualTo(Imports.ROWS);
        assertThat(Imports.instances(id)).containsExactly(0, 1);
    }
}
