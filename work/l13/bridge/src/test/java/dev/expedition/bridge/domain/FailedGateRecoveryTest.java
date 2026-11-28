package dev.expedition.bridge.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Verification conditions for failed-gate recovery: bounded retries, no overwrite, no reward refresh. */
class FailedGateRecoveryTest {
    private static final PortalRecord.Site ENTRANCE=new PortalRecord.Site("minecraft:overworld",10,70,10);
    private static final PortalRecord.Destination DEST=new PortalRecord.Destination("dimdoors:dungeon",12,
            new BlockBounds(100,0,100,300,250,300),new PortalRecord.Site("dimdoors:dungeon",110,10,110));
    private static DungeonSpec camp() {
        return new DungeonSpec("test:camp",DungeonSpec.Provider.WDA,"dungeons_arise:oak_camp",Set.of(1,2,3,4,5),10,
                new DungeonSpec.Environment(96,16,8,16,IslandPlan.Surface.GRASS),2);
    }
    private PortalRecord fresh() { return new PortalRecord(UUID.randomUUID(),PortalKind.EXPLORATION,3,ENTRANCE,42); }

    @Test void preAllocationFailureRetriesKeepPinnedContentAndSeed() {
        var r=fresh();var spec=camp();r.selectContent(new DungeonCatalog.Choice(3,spec));
        r.preparationStarted(spec.target());
        r.retryableFailure("planner failed");
        assertEquals(PortalRecord.Phase.NEW,r.phase());
        assertEquals(1,r.generationAttempts());
        assertEquals(spec.id(),r.selection().content().id());
        assertEquals(42,r.seed());
        assertFalse(r.failure().isBlank());
    }
    @Test void retryExhaustionQuarantinesWithReason() {
        var r=fresh();
        for(int i=0;i<PortalRecord.MAX_GENERATION_ATTEMPTS;i++) {
            if(r.phase()==PortalRecord.Phase.NEW)r.beginRetryAttempt();
            r.retryableFailure("planner failed #"+i);
        }
        assertEquals(PortalRecord.Phase.FAILED,r.phase());
        assertEquals(PortalRecord.MAX_GENERATION_ATTEMPTS,r.generationAttempts());
        assertTrue(r.failure().contains("planner failed"));
    }
    @Test void allocatedFailureCannotRetryAndQuarantines() {
        var r=fresh();r.reserve("test:camp",DEST);r.generationStarted();
        assertThrows(IllegalStateException.class,()->r.retryableFailure("late failure"));
        r.quarantine("late failure");
        assertEquals(PortalRecord.Phase.FAILED,r.phase());
        assertNotNull(r.destination());
    }
    @Test void interruptedPreparationAndGenerationStillQuarantine() {
        var preparing=fresh();preparing.preparationStarted("test:camp");
        preparing.recoverInterruptedGeneration();
        assertEquals(PortalRecord.Phase.FAILED,preparing.phase());
        assertNull(preparing.destination());
        var generating=fresh();generating.reserve("test:camp",DEST);generating.generationStarted();
        generating.recoverInterruptedGeneration();
        assertEquals(PortalRecord.Phase.FAILED,generating.phase());
        assertNotNull(generating.destination());
    }
    @Test void adminRetryDoesNotReopenUnknownAllocationAfterInterruption() {
        var r=fresh();
        r.preparationStarted("test:camp");
        r.recoverInterruptedGeneration();
        assertNull(r.destination());
        assertThrows(IllegalStateException.class,r::adminRetry);
        assertEquals(PortalRecord.Phase.FAILED,r.phase());
    }

    @Test void adminRetryOnlyReopensUnallocatedFailures() {
        var r=fresh();
        r.retryableFailure("first");r.retryableFailure("second");
        r.retryableFailure("third"); // exhausts
        assertEquals(PortalRecord.Phase.FAILED,r.phase());
        r.adminRetry();
        assertEquals(PortalRecord.Phase.NEW,r.phase());
        assertEquals(0,r.generationAttempts());
        assertTrue(r.failure().isBlank());
        var allocated=fresh();allocated.reserve("test:camp",DEST);allocated.quarantine("allocated failure");
        assertThrows(IllegalStateException.class,allocated::adminRetry);
    }
    @Test void adminReassignKeepsOldSpaceRewardsAndJourney() {
        var r=fresh();var spec=camp();r.selectContent(new DungeonCatalog.Choice(3,spec));
        r.reserve(spec.target(),DEST);r.generationStarted();
        r.installProtectedBlocks(List.of(new PortalRecord.Site("dimdoors:dungeon",110,10,110)));
        r.registerExit(new PortalRecord.Site("dimdoors:dungeon",200,10,200));
        r.registerInternalLink(new PortalRecord.Site("dimdoors:dungeon",111,11,111),new PortalRecord.Site("dimdoors:dungeon",112,11,112));
        r.generationComplete();
        var player=UUID.randomUUID();r.entered(player);r.recordBossDefeat("guardian");
        r.quarantine("build failed");
        r.adminReassign();
        assertEquals(PortalRecord.Phase.NEW,r.phase());
        assertNull(r.destination());
        assertTrue(r.exits().isEmpty());
        assertTrue(r.internalLinks().isEmpty());
        assertTrue(r.protectedBlocks().isEmpty());
        assertEquals(0,r.generationAttempts());
        assertTrue(r.failure().isBlank());
        assertNotNull(r.selection());
        assertEquals(spec.id(),r.selection().content().id());
        assertEquals(42,r.seed());
        assertEquals(ENTRANCE,r.entrance());
        assertTrue(r.defeatedBosses().contains("guardian"));
        assertTrue(r.visits().enteredBefore());
    }
    @Test void reassignedInstanceCanGenerateAgainFromANewReservation() {
        var r=fresh();var spec=camp();r.selectContent(new DungeonCatalog.Choice(3,spec));
        r.reserve(spec.target(),DEST);r.generationStarted();r.quarantine("partial build");
        r.adminReassign();
        assertEquals(PortalRecord.Phase.NEW,r.phase());
        var second=new PortalRecord.Destination("dimdoors:dungeon",77,
                new BlockBounds(0,0,0,100,100,100),new PortalRecord.Site("dimdoors:dungeon",2,2,2));
        r.reserve(spec.target(),second);r.generationStarted();
        assertEquals(PortalRecord.Phase.GENERATING,r.phase());
        assertEquals(77,r.destination().nativePocketId());
    }
    @Test void retryDoesNotReRollSeedOrContent() {
        var r=fresh();var spec=camp();r.selectContent(new DungeonCatalog.Choice(3,spec));
        r.preparationStarted(spec.target());r.retryableFailure("transient");
        var idBefore=r.selection().content().id();var seedBefore=r.seed();
        r.beginRetryAttempt();r.retryableFailure("transient again");
        assertEquals(idBefore,r.selection().content().id());
        assertEquals(seedBefore,r.seed());
    }
}
