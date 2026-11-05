package dev.expedition.bridge.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PortalLifecycleTest {
    private PortalRecord ready(PortalKind kind) {
        var r=new PortalRecord(UUID.randomUUID(),kind,kind==PortalKind.EXPLORATION?5:0,new PortalRecord.Site("minecraft:overworld",10,70,10),42);
        r.reserve("dungeons_arise:aviary",new PortalRecord.Destination("dimdoors:dungeon",12,new BlockBounds(100,0,100,300,250,300),new PortalRecord.Site("dimdoors:dungeon",110,10,110)));
        r.generationStarted();r.generationComplete();return r;
    }
    @Test void unusedSpecialEntranceDoesNotClose() { assertFalse(ready(PortalKind.GOLD).beginClosingIfEmpty()); }
    @Test void limboOnlyUnstableVisitMayCloseWithoutGeneratingAnUnusedDungeon() {
        var r=new PortalRecord(UUID.randomUUID(),PortalKind.UNSTABLE,0,new PortalRecord.Site("minecraft:overworld",0,70,0),1);
        var player=UUID.randomUUID();r.visits().entered(player);assertFalse(r.beginClosingIfEmpty());
        r.visits().confirmedSafeExit(player);assertTrue(r.beginClosingIfEmpty());assertNull(r.destination());assertNull(r.selection());
    }
    @Test void waitingPlayerLogoutIsRetainedAndNoLongerEligibleForAutomaticEntry() {
        var ledger=new VisitLedger();var player=UUID.randomUUID();ledger.entryIntent(player);ledger.disconnected(player);
        assertEquals(VisitLedger.Presence.DISCONNECTED,ledger.snapshot().get(player));assertFalse(ledger.enteredBefore());
    }
    @Test void livingSpaceContextMustBeReleasedBeforeSpecialEntranceCloses() {
        var r=ready(PortalKind.GOLD);var player=UUID.randomUUID();
        r.bindReturnAnchor(player,r.entrance());r.entered(player);r.livingVisit(player,true);
        r.visits().confirmedSafeExit(player);assertFalse(r.beginClosingIfEmpty());
        r.livingVisit(player,false);assertTrue(r.beginClosingIfEmpty());
    }
    @Test void failedTransferDoesNotCountAsAnActualVisit() {
        var r=ready(PortalKind.GOLD);var p=UUID.randomUUID();r.visits().entryIntent(p);
        r.visits().confirmedSafeExit(p);assertFalse(r.beginClosingIfEmpty());
        r.visits().entryIntent(p);r.entered(p);r.visits().confirmedSafeExit(p);assertTrue(r.beginClosingIfEmpty());
    }
    @Test void pendingTransferBlocksClosureEvenAfterOtherPlayersLeave() {
        var r=ready(PortalKind.GOLD);var a=UUID.randomUUID();var b=UUID.randomUUID();
        r.entered(a);r.visits().entryIntent(b);r.visits().confirmedSafeExit(a);r.visits().disconnected(b);
        assertFalse(r.beginClosingIfEmpty());
    }
    @Test void oneExitDoesNotStrandDisconnectedPartner() {
        var r=ready(PortalKind.GOLD);UUID a=UUID.randomUUID(),b=UUID.randomUUID();r.entered(a);r.entered(b);
        r.visits().disconnected(b);r.visits().confirmedSafeExit(a);assertFalse(r.beginClosingIfEmpty());
        var restored=new VisitLedger(r.visits().snapshot());assertFalse(restored.allSafelyExited());
        r.visits().confirmedSafeExit(b);assertTrue(r.beginClosingIfEmpty());
        assertThrows(IllegalStateException.class,()->r.entered(a));r.entranceRemoved();assertEquals(PortalRecord.Phase.CLOSED,r.phase());
        assertNotNull(r.destination());
    }
    @Test void deathWaitsForConfirmedSafeRespawn() {
        var r=ready(PortalKind.UNSTABLE);var player=UUID.randomUUID();r.entered(player);r.visits().died(player);r.visits().disconnected(player);
        assertEquals(VisitLedger.Presence.AWAITING_RESPAWN,r.visits().snapshot().get(player));assertFalse(r.beginClosingIfEmpty());
        r.visits().confirmedSafeExit(player);assertTrue(r.beginClosingIfEmpty());
    }
    @Test void permanentEntranceAndDefeatedBossStateSurviveRepeatVisits() {
        var r=ready(PortalKind.EXPLORATION);var p=UUID.randomUUID();r.entered(p);r.visits().confirmedSafeExit(p);
        assertFalse(r.beginClosingIfEmpty());assertTrue(r.recordBossDefeat("guardian"));assertFalse(r.recordBossDefeat("guardian"));
        r.entered(p);assertTrue(r.defeatedBosses().contains("guardian"));assertEquals(PortalRecord.Phase.READY,r.phase());
    }
    @Test void generationCannotBeRepeatedAfterInterruption() {
        var r=new PortalRecord(UUID.randomUUID(),PortalKind.EXPLORATION,1,new PortalRecord.Site("minecraft:overworld",0,70,0),2);
        r.reserve("example:structure",new PortalRecord.Destination("dimdoors:dungeon",1,new BlockBounds(0,0,0,20,20,20),new PortalRecord.Site("dimdoors:dungeon",2,2,2)));
        r.generationStarted();r.recoverInterruptedGeneration();assertEquals(PortalRecord.Phase.FAILED,r.phase());
        assertNotNull(r.destination());assertThrows(IllegalStateException.class,r::generationStarted);
    }
    @Test void boundsCheckWholeBuildingAndIntegerOverflow() {
        var pocket=new BlockBounds(-100,0,-100,100,255,100);
        assertTrue(pocket.contains(new BlockBounds(-99,1,-99,99,254,99)));
        assertFalse(pocket.contains(new BlockBounds(-101,1,-99,99,254,99)));
        assertTrue(pocket.intersects(new BlockBounds(100,20,20,120,40,40)));
        assertFalse(pocket.intersects(new BlockBounds(101,20,20,120,40,40)));
        assertThrows(ArithmeticException.class,()->new BlockBounds(Integer.MAX_VALUE,0,0,Integer.MAX_VALUE,0,0).expand(1));
    }
}
