package dev.expedition.bridge.storage;

import dev.expedition.bridge.domain.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PortalPersistenceTest {
    private PortalRecord portal() {
        return new PortalRecord(UUID.randomUUID(),PortalKind.GOLD,0,new PortalRecord.Site("minecraft:overworld",4,70,7),99);
    }
    @Test void saveReloadPreservesDestinationAndDisconnectedParticipants() {
        var r=portal();r.preparationStarted("dimdoors:dungeon/upwards_t_treasure");
        r.reserve(r.structureId(),new PortalRecord.Destination("dimdoors:dungeon_pockets",9,new BlockBounds(100,0,200,130,30,230),new PortalRecord.Site("dimdoors:dungeon_pockets",104,5,215)));
        r.generationStarted();r.generationComplete();UUID a=UUID.randomUUID(),b=UUID.randomUUID();r.entered(a);r.entered(b);
        r.visits().confirmedSafeExit(a);r.visits().disconnected(b);r.recordBossDefeat("test:one_off");
        var data=new ExpeditionSavedData();data.add(r);
        var loaded=ExpeditionSavedData.load(data.save(new CompoundTag())).get(r.id());
        assertEquals(r.destination(),loaded.destination());assertEquals(r.structureId(),loaded.structureId());assertEquals(r.seed(),loaded.seed());
        assertEquals(VisitLedger.Presence.DISCONNECTED,loaded.visits().snapshot().get(b));
        assertTrue(loaded.defeatedBosses().contains("test:one_off"));assertFalse(loaded.beginClosingIfEmpty());
        assertThrows(IllegalStateException.class,()->loaded.preparationStarted("another:reward"));
    }
    @Test void interruptedNativeAllocationCannotCreateASecondRewardRoom() {
        var r=portal();r.preparationStarted("dimdoors:dungeon/upwards_t_treasure");
        var data=new ExpeditionSavedData();data.add(r);
        var loaded=ExpeditionSavedData.load(data.save(new CompoundTag())).get(r.id());
        assertEquals(PortalRecord.Phase.FAILED,loaded.phase());
        assertThrows(IllegalStateException.class,()->loaded.reserve("retry",new PortalRecord.Destination("dimdoors:dungeon_pockets",1,new BlockBounds(0,0,0,2,2,2),new PortalRecord.Site("dimdoors:dungeon_pockets",1,1,1))));
    }
    @Test void unknownSchemaAndDuplicateEntrancesAreRejected() {
        var bad=new CompoundTag();bad.putInt("Schema",99);
        assertThrows(IllegalStateException.class,()->ExpeditionSavedData.load(bad));
        var data=new ExpeditionSavedData();data.add(portal());
        assertThrows(IllegalArgumentException.class,()->data.add(portal()));
    }
    @Test void crashBeforeRiftRegistrationRetainsTheCompleteReservation() {
        var r=portal();r.preparationStarted("dimdoors:dungeon/upwards_t_treasure");
        var reserved=new PortalRecord.Destination("dimdoors:dungeon_pockets",9,new BlockBounds(100,0,200,132,32,232),
                new PortalRecord.Site("dimdoors:dungeon_pockets",100,0,200));
        r.reserve(r.structureId(),reserved);var data=new ExpeditionSavedData();data.add(r);
        var loaded=ExpeditionSavedData.load(data.save(new CompoundTag())).get(r.id());
        assertEquals(PortalRecord.Phase.FAILED,loaded.phase());assertEquals(reserved,loaded.destination());
        assertThrows(IllegalStateException.class,()->loaded.resolveReservedArrival(reserved));
        assertThrows(IllegalStateException.class,()->NativeReservation.validate(reserved.dimension(),10,reserved.bounds(),0,256,java.util.List.of(loaded)));
    }
}
