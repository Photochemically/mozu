package dev.expedition.bridge.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TravelLedgerTest {
    private final UUID player=UUID.randomUUID(),portal=UUID.randomUUID();
    private final PortalRecord.Site door=new PortalRecord.Site("minecraft:overworld",-1,70,-17);
    private final PortalRecord.Site room=new PortalRecord.Site("dimdoors:dungeon_pockets",100,20,200);
    @Test void exactConfirmedProbabilitiesAndIndependentEnterExitPolicies() {
        assertEquals(300,TravelRoute.EXPLORATION_ENTER.limboBasisPoints());assertEquals(200,TravelRoute.EXPLORATION_EXIT.limboBasisPoints());
        assertEquals(500,TravelRoute.UNSTABLE_ENTER.limboBasisPoints());assertEquals(300,TravelRoute.UNSTABLE_EXIT.limboBasisPoints());
        for(var route:TravelRoute.values()) {
            if(route.limboBasisPoints()==0)continue;
            var hit=new TravelLedger().begin(player,portal,route,door,door,()->route.limboBasisPoints()-1);
            var miss=new TravelLedger().begin(player,portal,route,door,door,route::limboBasisPoints);
            assertEquals(TravelOperation.Outcome.LIMBO,hit.outcome());assertEquals(TravelOperation.Outcome.DESTINATION,miss.outcome());
        }
    }
    @Test void goldAndLivingSpaceNeverDrawRandomness() {
        for(var route:new TravelRoute[]{TravelRoute.GOLD_ENTER,TravelRoute.GOLD_EXIT,TravelRoute.LIVING_ENTER,TravelRoute.LIVING_EXIT}) {
            var op=new TravelLedger().begin(player,portal,route,door,door,()->{throw new AssertionError("Risk-free travel drew RNG");});
            assertEquals(TravelOperation.Outcome.DESTINATION,op.outcome());
        }
    }
    @Test void retryAndDuplicateCallbacksKeepIdentityAndOutcome() {
        var ledger=new TravelLedger();var draws=new AtomicInteger();
        var first=ledger.begin(player,portal,TravelRoute.EXPLORATION_ENTER,door,door,()->{draws.incrementAndGet();return 0;});
        var retry=ledger.begin(player,portal,TravelRoute.EXPLORATION_ENTER,door,door,()->{throw new AssertionError("Rerolled failed transit");});
        assertSame(first,retry);assertEquals(1,draws.get());
        first.bindLanding(new PortalRecord.Site("dimdoors:limbo",1,100,1));first.landed(10);
        assertSame(first,ledger.begin(player,portal,TravelRoute.EXPLORATION_ENTER,door,door,()->9999));
        assertThrows(IllegalStateException.class,()->first.bindLanding(room));
    }
    @Test void completedTravelRequiresCooldownAndLeavingBothTriggersBeforeNewDraw() {
        var ledger=new TravelLedger();var op=ledger.begin(player,portal,TravelRoute.GOLD_ENTER,door,door,()->0);
        op.bindLanding(room);op.landed(100);
        assertFalse(ledger.releaseIfClear(player,room,200));assertFalse(ledger.releaseIfClear(player,door,200));
        var clear=new PortalRecord.Site(room.dimension(),110,20,200);
        assertFalse(ledger.releaseIfClear(player,clear,119));assertTrue(ledger.releaseIfClear(player,clear,120));
        var next=ledger.begin(player,portal,TravelRoute.GOLD_EXIT,room,door,()->0);assertNotEquals(op.id(),next.id());
    }
    @Test void failedDoorDoesNotBlockAnotherDoorAndKeepsItsOwnPreviousOutcome() {
        var ledger=new TravelLedger();var first=ledger.begin(player,portal,TravelRoute.UNSTABLE_ENTER,door,door,()->0);
        var otherDoor=new PortalRecord.Site(door.dimension(),100,70,100);
        var other=ledger.begin(player,UUID.randomUUID(),TravelRoute.EXPLORATION_ENTER,otherDoor,otherDoor,()->9999);
        assertNotEquals(first.id(),other.id());assertEquals(2,ledger.operations().size());
        assertSame(first,ledger.begin(player,portal,TravelRoute.UNSTABLE_ENTER,door,door,()->9999));
        assertFalse(ledger.releaseIfClear(player,otherDoor,10000));
    }
    @Test void twoPlayersHaveIndependentTransitsAndCannotOverwriteAnchors() {
        var ledger=new TravelLedger();var a=ledger.begin(player,portal,TravelRoute.EXPLORATION_ENTER,door,door,()->0);
        var another=new PortalRecord.Site("minecraft:overworld",400,70,800);
        var b=ledger.begin(UUID.randomUUID(),portal,TravelRoute.EXPLORATION_ENTER,another,another,()->9999);
        assertNotEquals(a.id(),b.id());assertEquals(door,a.anchor());assertEquals(another,b.anchor());
        assertNotEquals(a.outcome(),b.outcome());
    }
    @Test void persistedOutcomeCannotBeChangedByChoosingAnIncompatibleLanding() {
        var ledger=new TravelLedger();var op=ledger.begin(player,portal,TravelRoute.GOLD_ENTER,door,door,()->0);
        assertThrows(IllegalArgumentException.class,()->op.bindLanding(new PortalRecord.Site("dimdoors:limbo",1,100,1)));
        assertThrows(IllegalArgumentException.class,()->op.bindLanding(door));
        assertThrows(IllegalStateException.class,()->op.landed(1));op.bindLanding(room);
    }
    @Test void changingExitsCannotRerollTheSameFailedDeparture() {
        var ledger=new TravelLedger();var first=ledger.begin(player,portal,TravelRoute.EXPLORATION_EXIT,room,door,()->0);
        var anotherExit=new PortalRecord.Site(room.dimension(),500,20,600);
        assertSame(first,ledger.begin(player,portal,TravelRoute.EXPLORATION_EXIT,anotherExit,door,()->{throw new AssertionError("Exit change rerolled");}));
    }
}
