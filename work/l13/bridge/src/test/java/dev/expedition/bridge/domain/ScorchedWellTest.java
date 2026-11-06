package dev.expedition.bridge.domain;

import dev.expedition.bridge.content.DungeonSpecJson;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.storage.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScorchedWellTest {
    private ScorchedWellLayout.Piece piece(String suffix,Cell origin,int sx,int sy,int sz,int count) {
        var p=new ScorchedWellLayout.Piece(ScorchedWellLayout.PREFIX+suffix,origin,0,new BlockBounds(0,0,0,0,0,0),List.of());
        var lava=new ArrayList<Cell>();for(int i=0;i<count;i++)lava.add(p.at(1+i%3,i/18,1+(i/3)%6));
        return new ScorchedWellLayout.Piece(p.template(),origin,0,p.expectedBounds(sx,sy,sz),lava);
    }
    private List<ScorchedWellLayout.Piece> pieces(){return List.of(piece("well_0",new Cell(0,0,0),12,20,12,108),piece("hub_part_0",new Cell(-19,-26,-19),24,26,24,43),piece("hub_part_1",new Cell(5,-26,-19),25,26,24,43),piece("hub_part_2",new Cell(5,-26,5),25,26,25,43),piece("hub_part_3",new Cell(-19,-26,5),24,26,25,43));}
    private ScorchedWellLayout layout(){return ScorchedWellLayout.fromPieces(pieces());}
    private ScorchedWellSiteSearch.Probe world(Cell blocked,Cell missing) {
        var l=layout();return new ScorchedWellSiteSearch.Probe() {
            public Type at(Cell p){if(p.equals(blocked))return Type.BLOCKED;if(p.x()==5&&p.z()==5&&p.y()>=-23&&p.y()<=15)return Type.AIR;if(p.y()==12||p.equals(l.rim().move(0,-1,0)))return Type.SUPPORT;return l.lava().contains(p)?Type.BLOCKED:Type.AIR;}
            public boolean lava(Cell p){return l.lava().contains(p)&&!p.equals(missing);}
        };
    }
    private ScorchedWellSiteSearch search(ScorchedWellSiteSearch.Probe probe,int budget){var s=new ScorchedWellSiteSearch(layout(),probe);int n=0;while(!s.complete()&&n++<1000)s.step(budget);assertTrue(s.complete());return s;}
    @Test void originalWellAndAllFourHubQuartersDefineGroundOutsideThePit() {
        var l=layout();assertEquals(280,l.lava().size());assertEquals(13,l.groundY());assertEquals(new Cell(14,13,5),l.arrival());assertEquals(new Cell(5,-23,5),l.shaftBottom());
        assertFalse(l.well().bounds().contains(l.arrival().x(),l.arrival().y(),l.arrival().z()));assertEquals(7,l.approach().size());
        assertEquals(Optional.of(l.arrival()),search(world(null,null),1).result());assertEquals(search(world(null,null),1).result(),search(world(null,null),256).result());
    }
    @Test void incompleteChangedAndSurfaceIntrudingGraphsAreRejected() {
        for(int i=0;i<5;i++){var list=new ArrayList<>(pieces());list.remove(i);assertThrows(IllegalArgumentException.class,()->ScorchedWellLayout.fromPieces(list));}
        var duplicate=new ArrayList<>(pieces());duplicate.add(duplicate.get(0));assertThrows(IllegalArgumentException.class,()->ScorchedWellLayout.fromPieces(duplicate));
        var shifted=new ArrayList<>(pieces());shifted.set(3,shifted.get(3).move(1,0,0));assertThrows(IllegalArgumentException.class,()->ScorchedWellLayout.fromPieces(shifted));
        var noLava=new ArrayList<>(pieces());var root=noLava.get(0);noLava.set(0,new ScorchedWellLayout.Piece(root.template(),root.origin(),0,root.bounds(),List.of()));assertThrows(IllegalArgumentException.class,()->ScorchedWellLayout.fromPieces(noLava));
        var intrudes=new ArrayList<>(pieces());intrudes.add(piece("corridor_0_normal",new Cell(30,10,30),9,6,19,0));assertThrows(IllegalArgumentException.class,()->ScorchedWellLayout.fromPieces(intrudes));
    }
    @Test void originalLavaMissingShaftPluggedAndOccupiedEntryFailWithoutBypassingHazards() {
        assertTrue(search(world(null,layout().lava().get(0)),256).result().isEmpty());
        for(var p:List.of(layout().arrival(),layout().arrival().move(1,-1,0),layout().rim(),layout().mouth(),layout().shaftBottom(),new Cell(9,15,5)))assertTrue(search(world(p,null),256).result().isEmpty());
        assertThrows(IllegalArgumentException.class,()->new ScorchedWellSiteSearch(layout(),world(null,null)).step(0));
    }
    @Test void completeLavaAndEntryCoordinatesMoveWithReservation() {
        var l=layout();var moved=l.move(-500,81,300);assertEquals(l.arrival().move(-500,81,300),moved.arrival());assertEquals(94,moved.groundY());
        assertEquals(l.lava().stream().map(p->p.move(-500,81,300)).toList(),moved.lava());assertEquals(l.shaftBottom().move(-500,81,300),moved.shaftBottom());
    }
    @Test void wellModeRequiresRockAndKnownTargetWhileLegacySelectionRemainsReadable() {
        for(var surface:List.of(IslandPlan.Surface.GRASS,IslandPlan.Surface.VOID))assertThrows(IllegalArgumentException.class,()->new DungeonSpec.Environment(96,8,6,24,surface,0,false,DungeonSpec.EntryMode.START_WELL));
        assertThrows(IllegalArgumentException.class,()->ScorchedWellLayout.validateMode("dungeons_arise:mushroom_mines",DungeonSpec.EntryMode.START_WELL));
        var old=new DungeonSpec("test:old",DungeonSpec.Provider.WDA,ScorchedWellLayout.TARGET,Set.of(3),1,new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.ROCK),1);
        assertEquals(old,DungeonSpecJson.read(old.id(),DungeonSpecJson.write(old)));assertThrows(IllegalArgumentException.class,()->ScorchedWellLayout.validateMode(old.target(),old.environment().entryMode()));
    }
    @Test void fixedWellSideArrivalSurvivesReadyReload() {
        var spec=new DungeonSpec("test:well",DungeonSpec.Provider.WDA,ScorchedWellLayout.TARGET,Set.of(3),1,new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.ROCK,0,false,DungeonSpec.EntryMode.START_WELL),1);
        assertEquals(spec,DungeonSpecJson.read(spec.id(),DungeonSpecJson.write(spec)));
        var p=new PortalRecord(UUID.randomUUID(),PortalKind.EXPLORATION,3,new PortalRecord.Site("minecraft:overworld",0,70,0),42);p.selectContent(new DungeonCatalog.Choice(3,spec));
        var a=new PortalRecord.Site("dimdoors:dungeon_pockets",14,13,5);p.reserve(spec.target(),new PortalRecord.Destination(a.dimension(),9,new BlockBounds(-50,-60,-50,50,80,50),new PortalRecord.Site(a.dimension(),0,0,0)));
        p.generationStarted();p.resolveWdaStartRoomArrival(a);p.registerExit(a);p.generationComplete();var data=new ExpeditionSavedData();data.add(p);
        var loaded=ExpeditionSavedData.load(data.save(new CompoundTag())).get(p.id());assertEquals(p.selection(),loaded.selection());assertEquals(p.destination(),loaded.destination());assertEquals(p.exits(),loaded.exits());assertThrows(IllegalStateException.class,()->loaded.resolveWdaStartRoomArrival(a));
    }
}
