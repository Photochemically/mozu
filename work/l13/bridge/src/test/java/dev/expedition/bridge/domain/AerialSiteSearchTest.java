package dev.expedition.bridge.domain;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AerialSiteSearchTest {
    private final InteriorRoom room=new InteriorRoom(new BlockBounds(-8,0,-8,8,8,8),List.of());
    private Type fullDeck(Cell p){return p.y()==1?Type.SUPPORT:Type.AIR;}
    private Optional<Cell> find(InteriorRoom r,InteriorSiteSearch.Probe p,List<Cell> exclude,int budget) {
        var search=new AerialSiteSearch(r,p,exclude);int loops=0;
        while(!search.complete() && loops++<200000)search.step(budget);
        assertTrue(search.complete());return search.result();
    }
    @Test void nativeDeckIsSelectedWithoutConstructingASingleSupportAndBudgetIsStable() {
        var a=find(room,this::fullDeck,List.of(),1).orElseThrow();assertEquals(2,a.y());
        assertEquals(a,find(room,this::fullDeck,List.of(),256).orElseThrow());assertTrue(InteriorSiteSearch.clearPlatform(a,this::fullDeck));
    }
    @Test void floatingDecorationOrClosedIsolatedCabinCannotPassAsTheConnectedDeck() {
        assertTrue(find(room,p->p.y()==1 && Math.abs(p.x())<=2 && Math.abs(p.z())<=2?Type.SUPPORT:Type.AIR,List.of(),64).isEmpty());
        assertTrue(find(room,p->p.y()==1 && p.x()>=5?Type.SUPPORT:Type.AIR,List.of(),64).isEmpty());
    }
    @Test void lowestDeckWinsOverRoofAndExistingExitsRemainSeparated() {
        InteriorSiteSearch.Probe decks=p->p.y()==1 || p.y()==6?Type.SUPPORT:Type.AIR;
        var first=find(room,decks,List.of(),64).orElseThrow();var next=find(room,decks,List.of(first),64).orElseThrow();
        assertEquals(2,first.y());assertEquals(2,next.y());assertTrue(InteriorSiteSearch.separated(first,next));
    }
    @Test void magmaTntLiquidsAndLowCeilingsNeverFormAnEntry() {
        for(String id:List.of("minecraft:tnt","minecraft:magma_block","minecraft:lava","minecraft:water"))assertEquals(Type.BLOCKED,InteriorBlockRules.classify(id,"",false,false));
        assertTrue(find(room,p->p.y()==4?Type.BLOCKED:fullDeck(p),List.of(),64).isEmpty());
        assertTrue(find(room,p->Type.AIR,List.of(),64).isEmpty());
    }
    @Test void hugeIndividualPiecesFailExplicitlyInsteadOfTruncatingSearch() {
        assertThrows(IllegalArgumentException.class,()->new AerialSiteSearch(new InteriorRoom(new BlockBounds(0,0,0,200,100,200),List.of()),this::fullDeck,List.of()));
    }
    @Test void offCenterNativeDeckIsNotConfusedWithTheEmptyCenterOfItsTemplate() {
        var r=new InteriorRoom(new BlockBounds(0,0,0,31,8,31),List.of());
        var result=find(r,p->p.y()==1 && p.x()>=25 && p.z()>=12 && p.z()<=30?Type.SUPPORT:Type.AIR,List.of(),64).orElseThrow();
        assertTrue(result.x()>=26);assertEquals(2,result.y());
    }
    @Test void carpetsCanCoverTheWalkingFloorButNeverTheDoorOrPlayersHead() {
        assertEquals(Type.FLOOR_DECOR,InteriorBlockRules.classify("minecraft:cyan_carpet","",false,false));
        InteriorSiteSearch.Probe carpet=p->p.y()==2 && p.x()==1?Type.FLOOR_DECOR:fullDeck(p);
        assertFalse(InteriorSiteSearch.clearPlatform(new Cell(1,2,0),carpet));
        assertTrue(InteriorSiteSearch.clearPlatform(new Cell(0,2,0),carpet));
        assertFalse(InteriorSiteSearch.clearPlatform(new Cell(0,2,0),p->p.y()==3?Type.FLOOR_DECOR:fullDeck(p)));
        assertTrue(find(room,carpet,List.of(),64).isPresent());
    }
    @Test void onlyDryUpsideDownStairsOfferACompleteTopSupportFace() {
        assertEquals(Type.SUPPORT,InteriorBlockRules.classify("minecraft:dark_oak_stairs","","top",false,false));
        assertEquals(Type.BLOCKED,InteriorBlockRules.classify("minecraft:dark_oak_stairs","","bottom",false,false));
        assertEquals(Type.BLOCKED,InteriorBlockRules.classify("minecraft:dark_oak_stairs","","top",true,false));
        assertEquals(Type.BLOCKED,InteriorBlockRules.classify("other:machine_stairs","","top",false,false));
    }
}
