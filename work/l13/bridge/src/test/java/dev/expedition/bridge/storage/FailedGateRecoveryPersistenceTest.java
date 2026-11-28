package dev.expedition.bridge.storage;

import dev.expedition.bridge.domain.PortalKind;
import dev.expedition.bridge.domain.PortalRecord;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FailedGateRecoveryPersistenceTest {
    private static PortalRecord fresh() {
        return new PortalRecord(UUID.randomUUID(),PortalKind.EXPLORATION,3,
                new PortalRecord.Site("minecraft:overworld",10,70,10),42);
    }
    @Test void attemptsRoundTripThroughPortalNbt() {
        var r=fresh();r.retryableFailure("transient planner failure");
        assertEquals(1,r.generationAttempts());
        var restored=PortalNbt.read(PortalNbt.write(r));
        assertEquals(1,restored.generationAttempts());
        assertEquals(PortalRecord.Phase.NEW,restored.phase());
        assertEquals("transient planner failure",restored.failure());
    }
    @Test void legacySaveWithoutAttemptsDefaultsToZero() {
        var r=fresh();var tag=PortalNbt.write(r);
        assertTrue(tag.contains("Attempts"));
        tag.remove("Attempts");
        assertEquals(0,PortalNbt.read(tag).generationAttempts());
    }
    @Test void failedAttemptsSurviveRoundTrip() {
        var r=fresh();
        for(int i=0;i<PortalRecord.MAX_GENERATION_ATTEMPTS;i++) {
            if(r.phase()==PortalRecord.Phase.NEW)r.beginRetryAttempt();
            r.retryableFailure("deterministic template error");
        }
        assertEquals(PortalRecord.Phase.FAILED,r.phase());
        var restored=PortalNbt.read(PortalNbt.write(r));
        assertEquals(PortalRecord.Phase.FAILED,restored.phase());
        assertEquals(PortalRecord.MAX_GENERATION_ATTEMPTS,restored.generationAttempts());
        assertTrue(restored.failure().contains("deterministic template error"));
    }
    @Test void negativeAttemptsAreRejected() {
        var r=fresh();
        assertThrows(IllegalArgumentException.class,()->r.restoreAttempts(-1));
        var tag=PortalNbt.write(r);tag.putInt("Attempts",-3);
        assertThrows(IllegalArgumentException.class,()->PortalNbt.read(tag));
    }
    @Test void adminReassignStateRoundTrips() {
        var r=fresh();
        r.reserve("test:camp",new PortalRecord.Destination("dimdoors:dungeon",12,
                new dev.expedition.bridge.domain.BlockBounds(100,0,100,300,250,300),
                new PortalRecord.Site("dimdoors:dungeon",110,10,110)));
        r.generationStarted();r.quarantine("partial build");r.adminReassign();
        var restored=PortalNbt.read(PortalNbt.write(r));
        assertEquals(PortalRecord.Phase.NEW,restored.phase());
        assertNull(restored.destination());
        assertEquals(0,restored.generationAttempts());
        assertTrue(restored.failure().isBlank());
    }
}
