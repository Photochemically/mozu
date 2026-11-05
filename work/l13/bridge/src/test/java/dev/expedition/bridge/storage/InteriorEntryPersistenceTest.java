package dev.expedition.bridge.storage;

import dev.expedition.bridge.content.DungeonSpecJson;
import dev.expedition.bridge.domain.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InteriorEntryPersistenceTest {
    private final PortalRecord.Site arrival=new PortalRecord.Site("dimdoors:dungeon_pockets",110,15,210);
    private PortalRecord reserved(DungeonSpec.EntryMode mode) {
        var p=new PortalRecord(UUID.randomUUID(),PortalKind.EXPLORATION,3,new PortalRecord.Site("minecraft:overworld",0,70,0),77);
        var spec=new DungeonSpec("test:mine",DungeonSpec.Provider.WDA,"dungeons_arise:mining_system",Set.of(3),1,
                new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.ROCK,0,false,mode),2);
        p.selectContent(new DungeonCatalog.Choice(3,spec));p.preparationStarted(spec.target());
        p.reserve(spec.target(),new PortalRecord.Destination(arrival.dimension(),42,new BlockBounds(100,0,200,150,100,250),new PortalRecord.Site(arrival.dimension(),145,96,225)));
        return p;
    }
    @Test void entryModeRoundTripsWhileOldSnapshotsKeepPerimeterBehavior() {
        var p=reserved(DungeonSpec.EntryMode.START_ROOM);var json=DungeonSpecJson.write(p.selection().content());
        assertEquals(p.selection().content(),DungeonSpecJson.read(p.selection().content().id(),json));
        json.getAsJsonObject("environment").remove("entry_mode");assertEquals(DungeonSpec.EntryMode.PERIMETER,DungeonSpecJson.read(p.selection().content().id(),json).environment().entryMode());
        json.getAsJsonObject("environment").addProperty("entry_mode","unknown");assertThrows(IllegalArgumentException.class,()->DungeonSpecJson.read(p.selection().content().id(),json));
        assertThrows(IllegalArgumentException.class,()->new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.GRASS,0,false,DungeonSpec.EntryMode.START_ROOM));
    }
    @Test void resolvedArrivalRetainsAllocationAndInterruptedLoadCannotRegenerate() {
        var p=reserved(DungeonSpec.EntryMode.START_ROOM);var old=p.destination();p.generationStarted();p.resolveWdaStartRoomArrival(arrival);
        assertEquals(old.bounds(),p.destination().bounds());assertEquals(old.nativePocketId(),p.destination().nativePocketId());assertEquals(old.dimension(),p.destination().dimension());
        var data=new ExpeditionSavedData();data.add(p);var restored=ExpeditionSavedData.load(data.save(new CompoundTag())).get(p.id());
        assertEquals(PortalRecord.Phase.FAILED,restored.phase());assertEquals(arrival,restored.destination().arrival());assertEquals(p.selection(),restored.selection());
        assertThrows(IllegalStateException.class,()->restored.resolveWdaStartRoomArrival(arrival));
    }
    @Test void fixedEntryAndAllExitsSurviveReadyReload() {
        var p=reserved(DungeonSpec.EntryMode.START_ROOM);p.generationStarted();p.resolveWdaStartRoomArrival(arrival);p.registerExit(arrival);
        p.registerExit(new PortalRecord.Site(arrival.dimension(),140,20,240));p.generationComplete();
        var data=new ExpeditionSavedData();data.add(p);var restored=ExpeditionSavedData.load(data.save(new CompoundTag())).get(p.id());
        assertEquals(PortalRecord.Phase.READY,restored.phase());assertEquals(p.destination(),restored.destination());assertEquals(p.exits(),restored.exits());
        assertThrows(IllegalStateException.class,()->restored.resolveWdaStartRoomArrival(arrival));
    }
    @Test void earlyWrongModeOutOfBoundsAndAlreadyInstalledExitsCannotMoveEntry() {
        var p=reserved(DungeonSpec.EntryMode.START_ROOM);assertThrows(IllegalStateException.class,()->p.resolveWdaStartRoomArrival(arrival));p.generationStarted();
        assertThrows(IllegalArgumentException.class,()->p.resolveWdaStartRoomArrival(new PortalRecord.Site(arrival.dimension(),0,0,0)));
        assertThrows(IllegalArgumentException.class,()->p.resolveWdaStartRoomArrival(new PortalRecord.Site("minecraft:overworld",110,15,210)));
        p.registerExit(arrival);assertThrows(IllegalStateException.class,()->p.resolveWdaStartRoomArrival(arrival));
        var perimeter=reserved(DungeonSpec.EntryMode.PERIMETER);perimeter.generationStarted();assertThrows(IllegalStateException.class,()->perimeter.resolveWdaStartRoomArrival(arrival));
    }
}
