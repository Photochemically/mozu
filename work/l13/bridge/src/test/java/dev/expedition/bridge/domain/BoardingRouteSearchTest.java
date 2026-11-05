package dev.expedition.bridge.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BoardingRouteSearchTest {
    private final IslandPlan island=new IslandPlan(new BlockBounds(0,0,0,9,20,9),4,4,2,4,IslandPlan.Surface.WATER,4);
    private BoardingRouteSearch.Block base(BoardingRouteSearch.Cell p) {
        var column=island.column(p.x(),p.z());
        if(column!=null && p.y()>=column.bottom()&&p.y()<=column.top())return island.material(p.x(),p.y(),p.z())==IslandPlan.Material.WATER
                ?BoardingRouteSearch.Block.WATER:BoardingRouteSearch.Block.SUPPORT;
        return BoardingRouteSearch.Block.AIR;
    }
    private Map<BoardingRouteSearch.Cell,BoardingRouteSearch.Block> deck() {
        var map=new HashMap<BoardingRouteSearch.Cell,BoardingRouteSearch.Block>();
        for(int x=3;x<=6;x++)for(int z=3;z<=6;z++)map.put(new BoardingRouteSearch.Cell(x,5,z),BoardingRouteSearch.Block.SUPPORT);
        return map;
    }
    private BoardingRouteSearch search(Map<BoardingRouteSearch.Cell,BoardingRouteSearch.Block> map) {
        return new BoardingRouteSearch(island,island.completeBounds().expand(16),p->map.getOrDefault(p,base(p)));
    }
    private void finish(BoardingRouteSearch search) {
        int n=0;while(!search.complete()&&n++<100000)search.step(1);assertTrue(search.complete());
    }
    @Test void incrementalSearchBuildsAReversibleStepRouteWithoutReplacingOriginalSolids() {
        var blocks=deck();var original=new HashMap<>(blocks);var search=search(blocks);search.step(1);assertFalse(search.complete());finish(search);
        var plan=search.result().orElseThrow();assertEquals(6,plan.landing().y());assertTrue(plan.deckCells()>=8);
        assertEquals(plan.additions().size(),new HashSet<>(plan.additions()).size());
        for(var p:plan.additions()) {
            assertFalse(original.containsKey(p));assertTrue(Set.of(BoardingRouteSearch.Block.AIR,BoardingRouteSearch.Block.WATER).contains(base(p)));
            blocks.put(p,BoardingRouteSearch.Block.SUPPORT);
        }
        for(int i=0;i<plan.feet().size();i++) {
            var p=plan.feet().get(i);assertEquals(BoardingRouteSearch.Block.AIR,blocks.getOrDefault(p,base(p)));
            assertEquals(BoardingRouteSearch.Block.AIR,blocks.getOrDefault(p.move(0,1,0),base(p.move(0,1,0))));
            assertEquals(BoardingRouteSearch.Block.SUPPORT,blocks.getOrDefault(p.move(0,-1,0),base(p.move(0,-1,0))));
            if(i>0)assertTrue(Math.abs(p.y()-plan.feet().get(i-1).y())<=1);
        }
        original.forEach((p,b)->assertEquals(b,blocks.get(p)));assertTrue(plan.additions().size()>plan.feet().size());
    }
    @Test void isolatedPostCannotBeMistakenForAnAccessibleDeck() {
        var blocks=new HashMap<BoardingRouteSearch.Cell,BoardingRouteSearch.Block>();blocks.put(new BoardingRouteSearch.Cell(5,5,5),BoardingRouteSearch.Block.SUPPORT);
        var search=search(blocks);finish(search);assertTrue(search.result().isEmpty());assertEquals(1,blocks.size());
    }
    @Test void aLongOuterBeamWithEnoughCellsStillNeedsAConnectionToTheHullInterior() {
        var blocks=new HashMap<BoardingRouteSearch.Cell,BoardingRouteSearch.Block>();
        for(int z=0;z<=9;z++)blocks.put(new BoardingRouteSearch.Cell(0,5,z),BoardingRouteSearch.Block.SUPPORT);
        var search=search(blocks);finish(search);assertTrue(search.result().isEmpty());assertEquals(10,blocks.size());
    }
    @Test void closedHullIsNeverCutOpenToForceBoarding() {
        var blocks=deck();for(int y=4;y<=22;y++)for(int x=2;x<=7;x++)for(int z=2;z<=7;z++)
            if(x==2||x==7||z==2||z==7)blocks.put(new BoardingRouteSearch.Cell(x,y,z),BoardingRouteSearch.Block.BLOCKED);
        var before=new HashMap<>(blocks);var search=search(blocks);finish(search);assertTrue(search.result().isEmpty());assertEquals(before,blocks);
    }
    @Test void twoBlockStandingClearanceDoesNotExcuseMissingJumpHeadroom() {
        var blocks=deck();for(int n=0;n<=9;n++)for(var p:List.of(new BoardingRouteSearch.Cell(-3,6,n),new BoardingRouteSearch.Cell(12,6,n),
                new BoardingRouteSearch.Cell(n,6,-3),new BoardingRouteSearch.Cell(n,6,12)))blocks.put(p,BoardingRouteSearch.Block.BLOCKED);
        var search=search(blocks);finish(search);assertTrue(search.result().isEmpty());
    }
    @Test void hazardContainersAndPartialFloorsAreNotDeckSupports() {
        for(String id:List.of("minecraft:magma_block","minecraft:tnt","minecraft:campfire","minecraft:barrel","minecraft:oak_fence","minecraft:oak_stairs","another:oak_planks"))
            assertEquals(BoardingRouteSearch.Block.BLOCKED,BoardingBlockRules.classify(id,"",false,false));
        assertEquals(BoardingRouteSearch.Block.BLOCKED,BoardingBlockRules.classify("minecraft:oak_planks","",false,true));
        assertEquals(BoardingRouteSearch.Block.BLOCKED,BoardingBlockRules.classify("minecraft:oak_slab","bottom",false,false));
        assertEquals(BoardingRouteSearch.Block.BLOCKED,BoardingBlockRules.classify("minecraft:oak_slab","top",true,false));
        assertEquals(BoardingRouteSearch.Block.SUPPORT,BoardingBlockRules.classify("minecraft:oak_slab","top",false,false));
    }
    @Test void resultIsDeterministicAcrossDifferentWorkBudgets() {
        var a=search(deck());var b=search(deck());finish(a);while(!b.complete())b.step(31);assertEquals(a.result(),b.result());
        assertThrows(IllegalArgumentException.class,()->b.step(0));
    }
}
