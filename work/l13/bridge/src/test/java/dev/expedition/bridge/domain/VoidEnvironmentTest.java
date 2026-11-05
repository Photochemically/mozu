package dev.expedition.bridge.domain;

import dev.expedition.bridge.content.DungeonSpecJson;
import dev.expedition.bridge.storage.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VoidEnvironmentTest {
    @Test void surfaceProjectionFailsAndMechanicalGatesRequireTheirDedicatedAdapter() {
        assertDoesNotThrow(()->AerialContentPolicy.validate("dungeons_arise:aviary","minecraft:jigsaw",false));
        assertThrows(IllegalArgumentException.class,()->AerialContentPolicy.validate("dungeons_arise:small_blimp","minecraft:jigsaw",true));
        assertDoesNotThrow(()->AerialContentPolicy.validate("dungeons_arise:mechanical_nest","minecraft:jigsaw",false));
        assertTrue(AerialContentPolicy.needsGateBridge("dungeons_arise:mechanical_nest"));
        assertFalse(AerialContentPolicy.needsGateBridge("dungeons_arise:aviary"));
        assertThrows(IllegalArgumentException.class,()->AerialContentPolicy.validate("dungeons_arise:future","other:unknown",false));
    }
    @Test void emptyBackgroundHasNoTerrainWritesOrInventedGroundBelowTheBuilding() {
        var b=new BlockBounds(-20,190,-30,20,240,30);var plan=new IslandPlan(b,96,8,6,24,IslandPlan.Surface.VOID);
        assertFalse(plan.hasTerrain());assertFalse(new IslandCursor(plan).hasNext());assertFalse(new IslandCursor(plan,true).hasNext());
        assertNull(plan.column(0,0));assertNull(plan.solidColumn(0,0));assertNull(plan.fluidColumn(0,0));assertEquals(b,plan.completeBounds());
        assertTrue(b.contains(plan.entryX(),plan.entryY(),plan.entryZ()));
        var move=plan.move(-100,-174,400);assertEquals(b.move(-100,-174,400),move.completeBounds());assertFalse(new IslandCursor(move).hasNext());
        assertEquals(83,ReservationGeometry.plan(b,16,0,256).padded().height());
        assertThrows(IllegalArgumentException.class,()->ReservationGeometry.plan(new BlockBounds(0,0,0,10,250,10),16,0,256));
    }
    @Test void voidAndDeckModeMustBePairedAndCannotInheritWaterOrBoarding() {
        assertThrows(IllegalArgumentException.class,()->new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.VOID));
        assertThrows(IllegalArgumentException.class,()->new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.ROCK,0,false,DungeonSpec.EntryMode.START_DECK));
        assertThrows(IllegalArgumentException.class,()->new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.VOID,12,false,DungeonSpec.EntryMode.START_DECK));
        assertThrows(IllegalArgumentException.class,()->new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.VOID,0,true,DungeonSpec.EntryMode.START_DECK));
    }
    @Test void aerialSelectionFixedArrivalAndExitsSurviveReadyReload() {
        for(String target:List.of("dungeons_arise:heavenly_rider","dungeons_arise:mechanical_nest")) {
        var spec=new DungeonSpec("test:air",DungeonSpec.Provider.WDA,target,Set.of(4),1,
                new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.VOID,0,false,DungeonSpec.EntryMode.START_DECK),1);
        assertEquals(spec,DungeonSpecJson.read(spec.id(),DungeonSpecJson.write(spec)));
        var p=new PortalRecord(UUID.randomUUID(),PortalKind.EXPLORATION,4,new PortalRecord.Site("minecraft:overworld",0,70,0),42);p.selectContent(new DungeonCatalog.Choice(4,spec));
        var arrival=new PortalRecord.Site("dimdoors:dungeon_pockets",15,30,15);
        p.reserve(spec.target(),new PortalRecord.Destination(arrival.dimension(),9,new BlockBounds(0,0,0,50,100,50),new PortalRecord.Site(arrival.dimension(),0,0,0)));p.generationStarted();
        p.resolveWdaStartRoomArrival(arrival);p.registerExit(arrival);p.generationComplete();var data=new ExpeditionSavedData();data.add(p);
        var loaded=ExpeditionSavedData.load(data.save(new CompoundTag())).get(p.id());
        assertEquals(p.selection(),loaded.selection());assertEquals(p.destination(),loaded.destination());assertEquals(p.exits(),loaded.exits());
        assertThrows(IllegalStateException.class,()->loaded.resolveWdaStartRoomArrival(arrival));
        }
    }
}
