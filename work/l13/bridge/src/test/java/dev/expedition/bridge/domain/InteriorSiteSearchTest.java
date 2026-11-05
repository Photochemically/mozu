package dev.expedition.bridge.domain;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InteriorSiteSearchTest {
    private final BlockBounds bounds=new BlockBounds(0,0,0,20,6,20);
    private final InteriorRoom room=new InteriorRoom(bounds,List.of(new Cell(0,1,10)));
    private Type floor(Cell p){return p.y()==0?Type.SUPPORT:Type.AIR;}
    private Optional<Cell> find(InteriorRoom r,InteriorSiteSearch.Probe p,List<Cell> excluded,int budget) {
        var search=new InteriorSiteSearch(r,p,excluded);int loops=0;
        while(!search.complete() && loops++<100000)search.step(budget);
        assertTrue(search.complete());return search.result();
    }
    @Test void searchBudgetsDoNotChangeTheDoorAndNeverSelectOutsideOriginalPiece() {
        var a=find(room,this::floor,List.of(),1).orElseThrow();
        assertEquals(a,find(room,this::floor,List.of(),256).orElseThrow());
        assertTrue(a.x()>0 && a.z()>0);assertTrue(InteriorSiteSearch.clearPlatform(a,this::floor));
    }
    @Test void aSafeButSealedCavityIsNotAnEntry() {
        InteriorSiteSearch.Probe p=c->c.y()==0?Type.SUPPORT:(c.x()<=2 && c.z()==10 || c.x()>=6 && c.x()<=15 && c.z()>=5 && c.z()<=15)?Type.AIR:Type.BLOCKED;
        assertTrue(find(room,p,List.of(),32).isEmpty());
    }
    @Test void railsMayBeTraversedButNeverReplacedByTheDoor() {
        InteriorSiteSearch.Probe p=c->c.y()==0?Type.SUPPORT:c.y()==1 && c.z()==10?Type.PASSABLE:Type.AIR;
        var found=find(room,p,List.of(),8).orElseThrow();assertNotEquals(10,found.z());
        assertFalse(InteriorSiteSearch.clearPlatform(new Cell(5,1,10),p));
    }
    @Test void headHeightJointAboveAnOriginalRailFindsTheSameWalkableComponent() {
        var raisedMarker=new InteriorRoom(bounds,List.of(new Cell(0,2,10)));
        InteriorSiteSearch.Probe p=c->c.y()==0?Type.SUPPORT:c.y()==1 && c.z()==10?Type.PASSABLE:Type.AIR;
        assertEquals(find(room,p,List.of(),16),find(raisedMarker,p,List.of(),16));
        assertTrue(find(raisedMarker,c->c.y()==1?Type.BLOCKED:p.at(c),List.of(),16).isEmpty());
    }
    @Test void insufficientHeadroomOrNoSafeSupportPreventsPlacement() {
        assertTrue(find(room,p->p.y()==0?Type.SUPPORT:p.y()==3?Type.BLOCKED:Type.AIR,List.of(),128).isEmpty());
        assertTrue(find(room,p->Type.AIR,List.of(),128).isEmpty());
    }
    @Test void movedNegativeCoordinatesAndPreviouslySelectedDoorsAreRespected() {
        var shifted=room.move(-100,-20,-300);
        var forbidden=new Cell(-99,-19,-290);
        var found=find(shifted,p->floor(p.move(100,20,300)),List.of(forbidden),16).orElseThrow();
        assertTrue(InteriorSiteSearch.separated(found,forbidden));assertTrue(shifted.bounds().contains(found.x(),found.y(),found.z()));
        assertEquals(new Cell(-100,-19,-290),shifted.passages().get(0));
    }
    @Test void liquidHazardAndFunctionalBlocksRemainExcluded() {
        for(String id:List.of("minecraft:lava","minecraft:water","minecraft:magma_block","minecraft:spawner","minecraft:chest","minecraft:stone_pressure_plate","minecraft:sculk_sensor","other:machine"))
            assertEquals(Type.BLOCKED,InteriorBlockRules.classify(id,"",false,false));
        assertEquals(Type.BLOCKED,InteriorBlockRules.classify("minecraft:stone","",true,false));
        assertEquals(Type.BLOCKED,InteriorBlockRules.classify("minecraft:stone","",false,true));
        assertEquals(Type.SUPPORT,InteriorBlockRules.classify("minecraft:polished_andesite","",false,false));
    }
    @Test void fullBuildingNeedsFourRoofBlocksWithoutChangingItsSize() {
        var enough=new IslandPlan(bounds,11,8,6,24,IslandPlan.Surface.ROCK);assertDoesNotThrow(()->InteriorRoom.requireRockCover(enough));
        assertThrows(IllegalArgumentException.class,()->InteriorRoom.requireRockCover(new IslandPlan(bounds,10,8,6,24,IslandPlan.Surface.ROCK)));
        assertThrows(IllegalArgumentException.class,()->InteriorRoom.requireRockCover(new IslandPlan(bounds,11,8,6,24,IslandPlan.Surface.GRASS)));
        assertEquals(bounds,enough.building());
    }
}
