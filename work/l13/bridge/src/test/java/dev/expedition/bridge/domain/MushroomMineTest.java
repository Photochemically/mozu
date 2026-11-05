package dev.expedition.bridge.domain;

import dev.expedition.bridge.content.DungeonSpecJson;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.storage.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MushroomMineTest {
    private final BlockBounds body=new BlockBounds(0,0,0,63,63,63);
    private final MushroomMineLayout layout=new MushroomMineLayout(body,new Cell(53,11,35));
    private List<MushroomMineLayout.Piece> pieces() {
        var result=new ArrayList<MushroomMineLayout.Piece>();
        int[][] offsets={{0,0,0},{32,0,0},{32,0,32},{0,0,32},{0,32,0},{32,32,0},{32,32,32},{0,32,32}};
        for(int i=0;i<8;i++)result.add(new MushroomMineLayout.Piece(MushroomMineLayout.PREFIX+"part_"+i,new Cell(offsets[i][0],offsets[i][1],offsets[i][2]),0));
        return result;
    }
    private Type ramp(Cell p) {
        if(p.y()<0)return Type.SUPPORT;
        // Wide native staircase from a low exterior shore to the original platform.
        if(p.x()>=52 && p.x()<=64 && p.z()>=33 && p.z()<=37) {
            int floor=Math.min(10,64-p.x());if(p.y()<=floor)return Type.SUPPORT;
        }
        return Type.AIR;
    }
    private MushroomMineSiteSearch search(InteriorSiteSearch.Probe probe,int budget) {
        var s=new MushroomMineSiteSearch(layout,probe);int steps=0;
        while(!s.complete() && steps++<100000)s.step(budget);assertTrue(s.complete());return s;
    }
    @Test void allCubesRequiredAndGroundIsStrictlyBelowEveryOriginalCell() {
        assertEquals(layout,MushroomMineLayout.fromPieces(pieces(),body));
        var island=new IslandPlan(body,layout.groundY(),8,6,24,IslandPlan.Surface.GRASS);var cursor=new IslandCursor(island);int count=0;
        while(cursor.hasNext()){var c=cursor.next();assertFalse(body.contains(c.x(),c.y(),c.z()));count++;}assertTrue(count>0);
        var moved=layout.move(-512,87,-300);assertEquals(new Cell(-459,98,-265),moved.platform());assertEquals(87,moved.groundY());
        assertEquals(120,ReservationGeometry.plan(island.completeBounds(),16,0,256).padded().height());
    }
    @Test void refusesPartialDuplicateMisalignedAndUnknownGraphs() {
        for(int i=0;i<8;i++){var list=new ArrayList<>(pieces());list.remove(i);assertThrows(IllegalArgumentException.class,()->MushroomMineLayout.fromPieces(list,body));}
        var duplicate=new ArrayList<>(pieces());duplicate.add(duplicate.get(0));assertThrows(IllegalArgumentException.class,()->MushroomMineLayout.fromPieces(duplicate,body));
        for(var p:List.of(new MushroomMineLayout.Piece(MushroomMineLayout.PREFIX+"part_7",new Cell(0,32,31),0),new MushroomMineLayout.Piece(MushroomMineLayout.PREFIX+"part_7",new Cell(0,32,32),1),new MushroomMineLayout.Piece("other:fake",new Cell(0,32,32),0))) {
            var list=new ArrayList<>(pieces());list.set(7,p);assertThrows(IllegalArgumentException.class,()->MushroomMineLayout.fromPieces(list,body));
        }
        assertThrows(IllegalArgumentException.class,()->MushroomMineLayout.fromPieces(pieces(),body.expand(1)));
    }
    @Test void walkRouteStartsOutsideAndEndsOnOriginalPlatformWithBoundedSteps() {
        var slow=search(this::ramp,1);var fast=search(this::ramp,256);assertEquals(slow.result(),fast.result());assertTrue(slow.result().isPresent());
        var route=slow.route();assertEquals(layout.platform(),route.get(route.size()-1));assertEquals(0,route.get(0).y());assertFalse(body.contains(route.get(0).x(),0,route.get(0).z()));
        for(int i=1;i<route.size();i++){var a=route.get(i-1);var b=route.get(i);assertEquals(1,Math.abs(a.x()-b.x())+Math.abs(a.z()-b.z()));assertTrue(Math.abs(a.y()-b.y())<=1);}
        assertThrows(IllegalArgumentException.class,()->new MushroomMineSiteSearch(layout,this::ramp).step(0));
    }
    @Test void missingSupportBlockedHeadroomAndOccupiedExteriorFailWithoutCarving() {
        assertTrue(search(p->p.equals(layout.platform())?Type.BLOCKED:ramp(p),256).result().isEmpty());
        assertTrue(search(p->p.equals(layout.platform().move(0,-1,0))?Type.AIR:ramp(p),256).result().isEmpty());
        assertTrue(search(p->p.y()==2 && (p.x()==64 || p.z()==64 || p.x()==-1 || p.z()==-1)?Type.BLOCKED:ramp(p),256).result().isEmpty());
        assertTrue(search(p->p.y()==0 && (p.x()==-3 || p.x()==66 || p.z()==-3 || p.z()==66)?Type.BLOCKED:ramp(p),256).result().isEmpty());
    }
    @Test void dedicatedModeRejectsWaterVoidAndOtherStructuresButRetainsOldSnapshotReading() {
        var e=new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.GRASS,0,false,DungeonSpec.EntryMode.START_MINE);
        var spec=new DungeonSpec("test:mine",DungeonSpec.Provider.WDA,MushroomMineLayout.TARGET,Set.of(3),1,e,1);
        assertEquals(spec,DungeonSpecJson.read(spec.id(),DungeonSpecJson.write(spec)));
        assertThrows(IllegalArgumentException.class,()->new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.WATER,12,false,DungeonSpec.EntryMode.START_MINE));
        assertThrows(IllegalArgumentException.class,()->new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.VOID,0,false,DungeonSpec.EntryMode.START_MINE));
        assertThrows(IllegalArgumentException.class,()->MushroomMineLayout.validateMode("dungeons_arise:scorched_mines",DungeonSpec.EntryMode.START_MINE));
        var old=new DungeonSpec("test:old",DungeonSpec.Provider.WDA,MushroomMineLayout.TARGET,Set.of(3),1,new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.GRASS),1);
        assertEquals(old,DungeonSpecJson.read(old.id(),DungeonSpecJson.write(old)));assertThrows(IllegalArgumentException.class,()->MushroomMineLayout.validateMode(old.target(),old.environment().entryMode()));
    }
    @Test void fixedExteriorArrivalAndModeSurviveReadySaveReload() {
        var spec=new DungeonSpec("test:mine",DungeonSpec.Provider.WDA,MushroomMineLayout.TARGET,Set.of(3),1,new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.ROCK,0,false,DungeonSpec.EntryMode.START_MINE),1);
        var p=new PortalRecord(UUID.randomUUID(),PortalKind.EXPLORATION,3,new PortalRecord.Site("minecraft:overworld",0,70,0),42);p.selectContent(new DungeonCatalog.Choice(3,spec));
        var arrival=new PortalRecord.Site("dimdoors:dungeon_pockets",66,0,35);
        p.reserve(spec.target(),new PortalRecord.Destination(arrival.dimension(),9,body.expand(16),new PortalRecord.Site(arrival.dimension(),0,0,0)));
        p.generationStarted();p.resolveWdaStartRoomArrival(arrival);p.registerExit(arrival);p.generationComplete();var data=new ExpeditionSavedData();data.add(p);
        var loaded=ExpeditionSavedData.load(data.save(new CompoundTag())).get(p.id());assertEquals(p.selection(),loaded.selection());assertEquals(p.destination(),loaded.destination());assertEquals(p.exits(),loaded.exits());
        assertThrows(IllegalStateException.class,()->loaded.resolveWdaStartRoomArrival(arrival));
    }
}
