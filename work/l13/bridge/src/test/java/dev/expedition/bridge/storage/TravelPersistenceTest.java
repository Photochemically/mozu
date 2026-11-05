package dev.expedition.bridge.storage;

import dev.expedition.bridge.domain.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TravelPersistenceTest {
    private final UUID player=UUID.randomUUID();
    private PortalRecord portal() {
        var portal=new PortalRecord(UUID.randomUUID(),PortalKind.GOLD,0,new PortalRecord.Site("minecraft:overworld",4,70,7),99);
        portal.reserve("native:reward",new PortalRecord.Destination("dimdoors:dungeon_pockets",1,new BlockBounds(100,0,100,130,30,130),
                new PortalRecord.Site("dimdoors:dungeon_pockets",110,10,110)));
        portal.generationStarted();portal.generationComplete();return portal;
    }
    @Test void reloadRetainsDrawAndLandingWithoutCallingRandomAgain() {
        var portal=portal();var data=new ExpeditionSavedData();data.add(portal);
        var op=data.travel().begin(player,portal.id(),TravelRoute.EXPLORATION_ENTER,portal.entrance(),portal.entrance(),()->0);
        var landing=new PortalRecord.Site("dimdoors:limbo",1,100,2);op.bindLanding(landing);
        var loaded=ExpeditionSavedData.load(data.save(new CompoundTag()));
        var retry=loaded.travel().begin(player,portal.id(),op.route(),op.source(),op.anchor(),()->{throw new AssertionError("Reload rerolled outcome");});
        assertEquals(op.id(),retry.id());assertEquals(op.outcome(),retry.outcome());assertEquals(landing,retry.landing());
        assertEquals(TravelOperation.State.PREPARED,retry.state());
    }
    @Test void anchorsLivingAbsenceAndManagedExitsSurviveReloadIndependently() {
        var portal=portal();var other=UUID.randomUUID();var otherAnchor=new PortalRecord.Site("minecraft:overworld",800,90,-200);
        portal.bindReturnAnchor(player,portal.entrance());portal.bindReturnAnchor(other,otherAnchor);
        portal.entered(player);portal.entered(other);portal.livingVisit(player,true);portal.visits().disconnected(player);
        portal.registerExit(portal.destination().arrival());
        var data=new ExpeditionSavedData();data.add(portal);var loaded=ExpeditionSavedData.load(data.save(new CompoundTag())).get(portal.id());
        assertEquals(portal.entrance(),loaded.returnAnchor(player));assertEquals(otherAnchor,loaded.returnAnchor(other));
        assertTrue(loaded.livingVisitors().contains(player));assertFalse(loaded.livingVisitors().contains(other));
        assertEquals(portal.exits(),loaded.exits());assertFalse(loaded.beginClosingIfEmpty());
    }
    @Test void completedOperationSurvivesRestartAndCannotTeleportOrRerollAgain() {
        var portal=portal();var data=new ExpeditionSavedData();data.add(portal);
        var op=data.travel().begin(player,portal.id(),TravelRoute.GOLD_ENTER,portal.entrance(),portal.entrance(),()->0);
        op.bindLanding(portal.destination().arrival());op.landed(500);
        var loaded=ExpeditionSavedData.load(data.save(new CompoundTag()));var restored=loaded.travel().operations().iterator().next();
        assertEquals(TravelOperation.State.LANDED,restored.state());assertEquals(520,restored.releaseAfter());
        assertFalse(loaded.travel().releaseIfClear(player,restored.landing(),10000));
    }
    @Test void schemaOneMigratesWithoutInventingHistoryOrExits() {
        var data=new ExpeditionSavedData();var portal=portal();data.add(portal);
        var old=data.save(new CompoundTag());old.putInt("Schema",1);old.remove("Travel");
        var restored=ExpeditionSavedData.load(old);
        assertTrue(restored.travel().operations().isEmpty());assertTrue(restored.get(portal.id()).exits().isEmpty());
        assertEquals(5,restored.save(new CompoundTag()).getInt("Schema"));
    }
}
