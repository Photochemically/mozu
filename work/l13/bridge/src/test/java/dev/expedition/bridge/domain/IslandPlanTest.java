package dev.expedition.bridge.domain;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class IslandPlanTest {
    @Test void undergroundBuildingHasFoundationAndClearArrivalOutsideItsFootprint() {
        var b=new BlockBounds(-40,-30,-20,50,25,80);
        var plan=new IslandPlan(b,32,8,6,12,IslandPlan.Surface.ROCK);
        assertTrue(plan.completeBounds().contains(b));
        assertEquals(-42,plan.bottom());
        assertEquals(31,plan.column(-40,-20).top());
        assertFalse(b.contains(plan.entryX(),32,plan.entryZ()));
        assertNotNull(plan.column(plan.entryX(),plan.entryZ()));
        assertTrue(plan.completeBounds().contains(plan.entryX(),34,plan.entryZ()));
    }
    @Test void cornersAreRoundedWithoutRemovingSupportUnderAnyBuildingCorner() {
        var plan=new IslandPlan(new BlockBounds(-18,4,-18,18,30,18),8,4,4,8,IslandPlan.Surface.GRASS);
        for(int x:new int[]{-18,18})for(int z:new int[]{-18,18})assertEquals(plan.bottom(),plan.column(x,z).bottom());
        assertNull(plan.column(plan.coreMaxX()+4,plan.coreMaxZ()+4));
        assertTrue(plan.column(plan.coreMaxX()+4,0).bottom()>plan.bottom());
    }
    @Test void negativeChunkTraversalProducesEveryCellExactlyOnce() {
        var plan=new IslandPlan(new BlockBounds(-18,0,-17,-14,2,-13),4,4,2,4,IslandPlan.Surface.ROCK);
        var seen=new HashSet<IslandCursor.Cell>();var cursor=new IslandCursor(plan);
        while(cursor.hasNext()) { assertTrue(cursor.hasNext());assertTrue(seen.add(cursor.next())); }
        assertFalse(cursor.hasNext());
        var b=plan.terrainBounds();int expected=0;
        for(int x=b.minX();x<=b.maxX();x++)for(int z=b.minZ();z<=b.maxZ();z++) {
            var c=plan.column(x,z);if(c==null)continue;
            for(int y=c.bottom();y<=c.top();y++){expected++;assertTrue(seen.contains(new IslandCursor.Cell(x,y,z)));}
        }
        assertEquals(expected,seen.size());
    }
    @Test void movingPlanPreservesItsDimensionsEntryAndTerrain() {
        var plan=new IslandPlan(new BlockBounds(-20,-8,-10,40,50,-1),16,6,4,8,IslandPlan.Surface.GRASS);
        var moved=plan.move(512,40,-1024);
        assertEquals(plan.completeBounds().move(512,40,-1024),moved.completeBounds());
        assertEquals(plan.entryX()+512,moved.entryX());
        assertEquals(plan.entryZ()-1024,moved.entryZ());
        assertEquals(plan.column(0,-5).bottom()+40,moved.column(512,-1029).bottom());
    }
    @Test void tooTallBuildingAndEnvironmentAreRejectedBeforeAllocationRatherThanCropped() {
        var plan=new IslandPlan(new BlockBounds(0,-90,0,120,160,120),96,8,6,24,IslandPlan.Surface.ROCK);
        assertThrows(IllegalArgumentException.class,()->ReservationGeometry.plan(plan.completeBounds(),16,0,256));
    }
}
