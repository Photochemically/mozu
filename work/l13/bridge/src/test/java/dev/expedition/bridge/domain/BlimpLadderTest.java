package dev.expedition.bridge.domain;

import dev.expedition.bridge.content.DungeonSpecJson;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.storage.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlimpLadderTest {
    private final BlimpLadderLayout layout=new BlimpLadderLayout(new Cell(0,10,0),new Cell(0,110,0),new Cell(0,111,1),0,-1);
    private final BlockBounds bounds=new BlockBounds(-27,10,-11,34,130,19);
    private List<BlimpLadderLayout.Piece> pieces() {
        return List.of(new BlimpLadderLayout.Piece(BlimpLadderLayout.LADDER,new Cell(0,58,0),0),
                new BlimpLadderLayout.Piece(BlimpLadderLayout.HULL0,new Cell(-27,106,-11),0),
                new BlimpLadderLayout.Piece(BlimpLadderLayout.LADDER,new Cell(0,10,0),0),
                new BlimpLadderLayout.Piece(BlimpLadderLayout.HULL1,new Cell(4,106,-11),0));
    }
    private BlimpLadderSiteSearch.Probe probe(Cell obstruction,Cell missing,boolean wrongFacing) {
        return new BlimpLadderSiteSearch.Probe() {
            public Type at(Cell p) {
                if(p.equals(obstruction))return Type.BLOCKED;
                if(p.y()==9 || p.equals(layout.landing().move(0,-1,0)))return Type.SUPPORT;
                if(p.x()==0 && p.z()==0 && p.y()>=10 && p.y()<=110)return Type.BLOCKED;
                return Type.AIR;
            }
            public boolean ladder(Cell p,int dx,int dz){return !p.equals(missing) && !wrongFacing && dx==0 && dz==-1;}
        };
    }
    private Optional<Cell> search(BlimpLadderSiteSearch.Probe probe,int budget) {
        var search=new BlimpLadderSiteSearch(layout,probe);int loops=0;
        while(!search.complete() && loops++<1000)search.step(budget);
        assertTrue(search.complete());return search.result();
    }
    @Test void actualChainMayExtendBelowRootAndMovesTogetherWithGround() {
        assertEquals(layout,BlimpLadderLayout.fromPieces(pieces(),bounds));
        var island=new IslandPlan(bounds,layout.foot().y(),8,6,24,IslandPlan.Surface.GRASS);
        assertEquals(9,island.terrainBounds().maxY());assertTrue(island.completeBounds().contains(layout.arrival().x(),10,layout.arrival().z()));
        var moved=layout.move(-200,40,500);assertEquals(layout.arrival().move(-200,40,500),moved.arrival());
        assertEquals(moved.foot().y(),island.move(-200,40,500).firstFreeY());
        assertEquals(Optional.of(layout.arrival()),search(probe(null,null,false),1));
        assertEquals(search(probe(null,null,false),1),search(probe(null,null,false),256));
    }
    @Test void rejectsMissingHullDisconnectedOverlappingAndRotatedChain() {
        var original=pieces();
        for(int remove=1;remove<4;remove++) {var copy=new ArrayList<>(original);copy.remove(remove);assertThrows(IllegalArgumentException.class,()->BlimpLadderLayout.fromPieces(copy,bounds));}
        var duplicate=new ArrayList<>(original);duplicate.add(original.get(0));assertThrows(IllegalArgumentException.class,()->BlimpLadderLayout.fromPieces(duplicate,bounds));
        for(var replacement:List.of(new BlimpLadderLayout.Piece(BlimpLadderLayout.LADDER,new Cell(0,11,0),0),
                new BlimpLadderLayout.Piece(BlimpLadderLayout.LADDER,new Cell(1,10,0),0),
                new BlimpLadderLayout.Piece(BlimpLadderLayout.LADDER,new Cell(0,10,0),1))) {
            var copy=new ArrayList<>(original);copy.set(2,replacement);assertThrows(IllegalArgumentException.class,()->BlimpLadderLayout.fromPieces(copy,bounds));
        }
        assertThrows(IllegalArgumentException.class,()->BlimpLadderLayout.fromPieces(original,bounds.move(0,-1,0)));
    }
    @Test void rejectsBrokenLadderWrongFacingBlockedClimbAndBothLandings() {
        assertTrue(search(probe(null,new Cell(0,60,0),false),16).isEmpty());
        assertTrue(search(probe(null,null,true),16).isEmpty());
        for(var blocked:List.of(new Cell(0,60,-1),new Cell(0,112,0),layout.landing(),layout.landing().move(0,-1,0),
                layout.arrival(),layout.arrival().move(1,-1,0),new Cell(0,10,-1),new Cell(0,9,0)))
            assertTrue(search(probe(blocked,null,false),16).isEmpty(),blocked.toString());
        assertThrows(IllegalArgumentException.class,()->new BlimpLadderSiteSearch(layout,probe(null,null,false)).step(0));
    }
    @Test void tallCompleteGraphFailsReservationRatherThanCroppingLadders() {
        var tall=new IslandPlan(new BlockBounds(-27,10,-11,34,226,19),10,8,6,24,IslandPlan.Surface.GRASS);
        assertThrows(IllegalArgumentException.class,()->ReservationGeometry.plan(tall.completeBounds(),16,0,256));
    }
    @Test void ladderModeIsExplicitDryAndOnlyAvailableForSmallBlimp() {
        for(var surface:List.of(IslandPlan.Surface.GRASS,IslandPlan.Surface.ROCK))assertDoesNotThrow(()->new DungeonSpec.Environment(96,8,6,24,surface,0,false,DungeonSpec.EntryMode.START_LADDER));
        assertThrows(IllegalArgumentException.class,()->new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.WATER,12,false,DungeonSpec.EntryMode.START_LADDER));
        assertThrows(IllegalArgumentException.class,()->new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.VOID,0,false,DungeonSpec.EntryMode.START_LADDER));
        assertThrows(IllegalArgumentException.class,()->BlimpLadderLayout.validateMode(BlimpLadderLayout.TARGET,DungeonSpec.EntryMode.PERIMETER));
        assertThrows(IllegalArgumentException.class,()->BlimpLadderLayout.validateMode("dungeons_arise:aviary",DungeonSpec.EntryMode.START_LADDER));
    }
    @Test void fixedGroundArrivalSurvivesReadyReloadWithoutRecomputingChain() {
        var spec=new DungeonSpec("test:blimp",DungeonSpec.Provider.WDA,BlimpLadderLayout.TARGET,Set.of(3),1,
                new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.GRASS,0,false,DungeonSpec.EntryMode.START_LADDER),1);
        assertEquals(spec,DungeonSpecJson.read(spec.id(),DungeonSpecJson.write(spec)));
        var p=new PortalRecord(UUID.randomUUID(),PortalKind.EXPLORATION,3,new PortalRecord.Site("minecraft:overworld",0,70,0),42);
        p.selectContent(new DungeonCatalog.Choice(3,spec));var arrival=new PortalRecord.Site("dimdoors:dungeon_pockets",0,10,-3);
        p.reserve(spec.target(),new PortalRecord.Destination(arrival.dimension(),9,bounds.expand(16),new PortalRecord.Site(arrival.dimension(),-27,10,-11)));
        p.generationStarted();p.resolveWdaStartRoomArrival(arrival);p.registerExit(arrival);p.generationComplete();
        var data=new ExpeditionSavedData();data.add(p);var loaded=ExpeditionSavedData.load(data.save(new CompoundTag())).get(p.id());
        assertEquals(p.selection(),loaded.selection());assertEquals(p.destination(),loaded.destination());assertEquals(p.exits(),loaded.exits());
        assertThrows(IllegalStateException.class,()->loaded.resolveWdaStartRoomArrival(arrival));
    }
    @Test void legacyBlimpSnapshotKeepsItsSavedArrivalWhileNewPlanningRejectsOldMode() {
        var legacy=new DungeonSpec("test:old_blimp",DungeonSpec.Provider.WDA,BlimpLadderLayout.TARGET,Set.of(3),1,
                new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.GRASS),1);
        var json=DungeonSpecJson.write(legacy);assertFalse(json.getAsJsonObject("environment").has("entry_mode"));
        assertEquals(legacy,DungeonSpecJson.read(legacy.id(),json));
        var p=new PortalRecord(UUID.randomUUID(),PortalKind.EXPLORATION,3,new PortalRecord.Site("minecraft:overworld",0,70,0),42);
        p.selectContent(new DungeonCatalog.Choice(3,legacy));var arrival=new PortalRecord.Site("dimdoors:dungeon_pockets",34,96,0);
        p.reserve(legacy.target(),new PortalRecord.Destination(arrival.dimension(),9,bounds.expand(16),arrival));
        p.generationStarted();p.registerExit(arrival);p.generationComplete();var data=new ExpeditionSavedData();data.add(p);
        var loaded=ExpeditionSavedData.load(data.save(new CompoundTag())).get(p.id());assertEquals(p.selection(),loaded.selection());assertEquals(p.destination(),loaded.destination());
        assertThrows(IllegalArgumentException.class,()->BlimpLadderLayout.validateMode(legacy.target(),legacy.environment().entryMode()));
    }
}
