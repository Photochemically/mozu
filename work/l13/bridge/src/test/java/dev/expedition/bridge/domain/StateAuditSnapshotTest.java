package dev.expedition.bridge.domain;

import dev.expedition.bridge.diagnostics.StateAuditSnapshot;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StateAuditSnapshotTest {
    private final PortalRecord.Site entrance=new PortalRecord.Site("minecraft:overworld",32,70,-16);
    private PortalRecord ready(PortalKind kind) {
        var p=new PortalRecord(UUID.randomUUID(),kind,kind==PortalKind.EXPLORATION?3:0,entrance,42);
        p.reserve("dimdoors:dungeon/test",new PortalRecord.Destination("dimdoors:dungeon_pockets",8,new BlockBounds(0,0,0,40,40,40),
                new PortalRecord.Site("dimdoors:dungeon_pockets",3,4,5)));p.generationStarted();p.generationComplete();return p;
    }
    @Test void temporaryLivingVisitsAndDisconnectedPlayersAreVisibleWithoutClosingTheDoor() {
        var p=ready(PortalKind.GOLD);var a=UUID.randomUUID();var b=UUID.randomUUID();
        for(var player:List.of(a,b)){p.bindReturnAnchor(player,entrance);p.entered(player);}
        p.livingVisit(a,true);p.visits().disconnected(b);
        var json=StateAuditSnapshot.capture(List.of(p),List.of());var row=json.getAsJsonArray("portals").get(0).getAsJsonObject();
        assertTrue(row.getAsJsonArray("closureBlockers").toString().contains("temporary_living_space_visit"));
        assertTrue(row.getAsJsonArray("participants").toString().contains("DISCONNECTED"));
        assertFalse(row.get("allParticipantsSafelyExited").getAsBoolean());assertEquals(PortalRecord.Phase.READY,p.phase());
        assertEquals(Set.of(a),p.livingVisitors());assertFalse(json.get("runtimeAcceptancePassed").getAsBoolean());
    }
    @Test void completedBossIdsAndPendingLimboDecisionAreObservedWithoutMutatingOrRerolling() {
        var p=ready(PortalKind.EXPLORATION);var player=UUID.randomUUID();p.bindReturnAnchor(player,entrance);p.recordBossDefeat("test:boss");
        var ledger=new TravelLedger();var op=ledger.begin(player,p.id(),TravelRoute.EXPLORATION_ENTER,entrance,entrance,()->0);
        var json=StateAuditSnapshot.capture(List.of(p),ledger.operations());
        assertTrue(json.getAsJsonArray("portals").toString().contains("test:boss"));
        assertEquals("LIMBO",json.getAsJsonArray("travelOperations").get(0).getAsJsonObject().get("outcome").getAsString());
        assertEquals(TravelOperation.State.PREPARED,op.state());assertEquals(1,ledger.operations().size());
        assertSame(op,ledger.begin(player,p.id(),TravelRoute.EXPLORATION_ENTER,entrance,entrance,()->{throw new AssertionError("rerolled");}));
        p.recordBossDefeat("test:later");assertFalse(json.toString().contains("test:later"));
    }
    @Test void stableOrderingSupportsComparisonsAndMissingDestinationStaysAbsent() {
        var a=ready(PortalKind.EXPLORATION);var b=new PortalRecord(UUID.randomUUID(),PortalKind.UNSTABLE,0,entrance,7);
        var first=StateAuditSnapshot.capture(List.of(a,b),List.of());var second=StateAuditSnapshot.capture(List.of(b,a),List.of());
        assertEquals(first,second);
        var newRow=first.getAsJsonArray("portals").asList().stream().map(v->v.getAsJsonObject()).filter(v->v.get("id").getAsString().equals(b.id().toString())).findFirst().orElseThrow();
        assertFalse(newRow.has("destination"));assertTrue(newRow.getAsJsonArray("closureBlockers").toString().contains("no_confirmed_arrival"));
        assertEquals(PortalRecord.Phase.NEW,b.phase());
    }
    @Test void internalMechanismsAreReportedSeparatelyFromOverworldExits() {
        var p=ready(PortalKind.EXPLORATION);p.registerExit(p.destination().arrival());
        p.registerInternalLink(new PortalRecord.Site("dimdoors:dungeon_pockets",10,2,10),new PortalRecord.Site("dimdoors:dungeon_pockets",20,2,20));
        var row=StateAuditSnapshot.capture(List.of(p),List.of()).getAsJsonArray("portals").get(0).getAsJsonObject();
        assertEquals(1,row.getAsJsonArray("exits").size());assertEquals(1,row.getAsJsonArray("internalLinks").size());
        assertEquals(1,p.internalLinks().size());
    }
}
