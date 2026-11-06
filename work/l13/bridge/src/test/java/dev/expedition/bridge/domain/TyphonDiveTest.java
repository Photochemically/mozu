package dev.expedition.bridge.domain;

import dev.expedition.bridge.content.DungeonSpecJson;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.storage.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TyphonDiveTest {
    private TyphonDiveLayout.Piece piece(int i) {
        var origin=new Cell((i-1)*48,59,0);var dummy=new TyphonDiveLayout.Piece(TyphonDiveLayout.PREFIX+i,origin,0,new BlockBounds(0,0,0,0,0,0),List.of(),List.of());
        var magma=new ArrayList<Cell>();for(int n=0;n<new int[]{47,49,28}[i];n++)magma.add(dummy.at(n%20,0,20+n/20));
        return new TyphonDiveLayout.Piece(dummy.template(),origin,0,dummy.expectedBounds(),magma,i==0?List.of(dummy.at(39,1,24)):i==1?List.of(dummy.at(28,7,24)):List.of());
    }
    private List<TyphonDiveLayout.Piece> pieces(){return List.of(piece(1),piece(0),piece(2));}
    private IslandPlan island(){return new IslandPlan(new BlockBounds(-48,59,0,95,106,47),96,8,6,24,IslandPlan.Surface.WATER,32);}
    private TyphonDiveLayout layout(){return TyphonDiveLayout.fromPieces(pieces(),island());}
    private TyphonDiveSearch.Probe probe(Cell blocked) {
        var l=layout();var island=island();return new TyphonDiveSearch.Probe() {
            public Type at(Cell p){if(p.equals(blocked))return Type.BLOCKED;var c=island.solidColumn(p.x(),p.z());if(c!=null&&p.y()>=c.bottom()&&p.y()<=c.top())return Type.SUPPORT;return island.inBasin(p.x(),p.z())&&p.y()<island.firstFreeY()?Type.BLOCKED:Type.AIR;}
            public boolean water(Cell p){return !p.equals(blocked)&&island.inBasin(p.x(),p.z())&&p.y()>=island.levels().solidFirstFreeY()&&p.y()<island.firstFreeY();}
            public boolean magma(Cell p){return !p.equals(blocked)&&l.magma().contains(p);}
            public boolean chest(Cell p){return !p.equals(blocked)&&l.chests().contains(p);}
            public boolean stone(Cell p){return !p.equals(blocked)&&p.equals(l.root().at(24,7,4));}
        };
    }
    private TyphonDiveSearch run(TyphonDiveSearch.Probe p,int count,int budget){var s=new TyphonDiveSearch(layout(),island(),count,p);int n=0;while(!s.complete()&&n++<1000)s.step(budget);assertTrue(s.complete());return s;}
    @Test void completeOriginalBodyKeepsSeabedOffsetAndAllRequestedDryShores() {
        var l=layout();assertEquals(124,l.magma().size());assertEquals(2,l.chests().size());assertEquals(new Cell(24,96,-3),l.shores(island(),1).get(0));
        for(int count=1;count<=4;count++){assertEquals(l.shores(island(),count),run(probe(null),count,1).result());assertEquals(run(probe(null),count,1).result(),run(probe(null),count,256).result());}
        assertThrows(IllegalArgumentException.class,()->new TyphonDiveSearch(l,island(),0,probe(null)));
        assertThrows(IllegalArgumentException.class,()->new TyphonDiveSearch(l,island(),1,probe(null)).step(0));
    }
    @Test void incompleteMisalignedChangedAndShallowGraphsFailBeforeWrites() {
        for(int i=0;i<3;i++){var p=new ArrayList<>(pieces());p.remove(i);assertThrows(IllegalArgumentException.class,()->TyphonDiveLayout.fromPieces(p,island()));}
        var duplicate=new ArrayList<>(pieces());duplicate.set(2,duplicate.get(0));assertThrows(IllegalArgumentException.class,()->TyphonDiveLayout.fromPieces(duplicate,island()));
        var moved=new ArrayList<>(pieces());moved.set(2,moved.get(2).move(1,0,0));assertThrows(IllegalArgumentException.class,()->TyphonDiveLayout.fromPieces(moved,island()));
        var root=piece(1);var changed=new TyphonDiveLayout.Piece(root.template(),root.origin(),0,root.bounds(),List.of(),root.chests());
        assertThrows(IllegalArgumentException.class,()->TyphonDiveLayout.fromPieces(List.of(changed,piece(0),piece(2)),island()));
        assertThrows(IllegalArgumentException.class,()->TyphonDiveLayout.fromPieces(pieces(),new IslandPlan(island().building(),96,8,6,24,IslandPlan.Surface.WATER,12)));
        assertThrows(IllegalArgumentException.class,()->layout().validateIsland(new IslandPlan(island().building(),67,8,6,24,IslandPlan.Surface.WATER,3)));
        assertThrows(IllegalArgumentException.class,()->ReservationGeometry.plan(island().completeBounds(),16,0,64));
    }
    @Test void missingHazardsTreasureDryBankOrWaterFailWithoutAlternateEntrances() {
        var l=layout();for(var p:List.of(l.magma().get(0),l.chests().get(0),l.root().at(24,7,4),l.root().at(24,7,3),new Cell(24,95,-1),new Cell(24,96,-2),new Cell(24,98,-2),new Cell(24,96,-3),new Cell(24,96,50)))
            assertTrue(run(probe(p),4,16).result().isEmpty(),"Should reject: "+p);
        assertEquals(1,run(probe(new Cell(24,96,50)),1,16).result().size());
    }
    @Test void fullBodyAndFixedShoreGeometryMoveWithReservation() {
        var moved=layout().move(-800,32,500);var island=island().move(-800,32,500);
        assertEquals(layout().shores(island(),4).stream().map(p->p.move(-800,32,500)).toList(),moved.shores(island,4));
        assertEquals(layout().magma().stream().map(p->p.move(-800,32,500)).toList(),moved.magma());assertEquals(layout().chests().stream().map(p->p.move(-800,32,500)).toList(),moved.chests());
    }
    @Test void modeRequiresExplicitWaterAndKnownTargetButOldSnapshotsRemainReadable() {
        for(int depth:new int[]{2,3})assertThrows(IllegalArgumentException.class,()->new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.WATER,depth,false,DungeonSpec.EntryMode.SHORE_DIVE));
        assertThrows(IllegalArgumentException.class,()->new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.ROCK,0,false,DungeonSpec.EntryMode.SHORE_DIVE));
        assertThrows(IllegalArgumentException.class,()->new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.WATER,32,true,DungeonSpec.EntryMode.SHORE_DIVE));
        assertThrows(IllegalArgumentException.class,()->TyphonDiveLayout.validateMode("dungeons_arise:illager_galley",DungeonSpec.EntryMode.SHORE_DIVE));
        var old=new DungeonSpec("test:old",DungeonSpec.Provider.WDA,TyphonDiveLayout.TARGET,Set.of(3),1,new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.WATER,12),1);
        assertEquals(old,DungeonSpecJson.read(old.id(),DungeonSpecJson.write(old)));assertThrows(IllegalArgumentException.class,()->TyphonDiveLayout.validateMode(old.target(),old.environment().entryMode()));
    }
    @Test void readyReloadKeepsWaterDepthArrivalAndAllShoreExits() {
        var spec=new DungeonSpec("test:dive",DungeonSpec.Provider.WDA,TyphonDiveLayout.TARGET,Set.of(3),1,new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.WATER,32,false,DungeonSpec.EntryMode.SHORE_DIVE),4);
        assertEquals(spec,DungeonSpecJson.read(spec.id(),DungeonSpecJson.write(spec)));
        var p=new PortalRecord(UUID.randomUUID(),PortalKind.EXPLORATION,3,new PortalRecord.Site("minecraft:overworld",0,70,0),42);p.selectContent(new DungeonCatalog.Choice(3,spec));
        var sites=layout().shores(island(),4).stream().map(c->new PortalRecord.Site("dimdoors:dungeon_pockets",c.x(),c.y(),c.z())).toList();
        var provisional=new PortalRecord.Site(sites.get(0).dimension(),island().entryX(),island().entryY(),island().entryZ());
        assertNotEquals(provisional,sites.get(0));
        p.reserve(spec.target(),new PortalRecord.Destination(provisional.dimension(),9,island().completeBounds().expand(16),provisional));
        p.generationStarted();p.resolveWdaStartRoomArrival(sites.get(0));sites.forEach(p::registerExit);p.generationComplete();
        var data=new ExpeditionSavedData();data.add(p);var loaded=ExpeditionSavedData.load(data.save(new CompoundTag())).get(p.id());
        assertEquals(p.selection(),loaded.selection());assertEquals(p.destination(),loaded.destination());assertEquals(p.exits(),loaded.exits());assertThrows(IllegalStateException.class,()->loaded.resolveWdaStartRoomArrival(sites.get(1)));
    }
}
