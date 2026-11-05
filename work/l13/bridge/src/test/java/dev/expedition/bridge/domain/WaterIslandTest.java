package dev.expedition.bridge.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WaterIslandTest {
    private IslandPlan basin(){return new IslandPlan(new BlockBounds(-18,-5,-17,-12,12,-11),8,4,2,4,IslandPlan.Surface.WATER,6);}
    @Test void waterHasFullFloorAndClosedSidesWithNoUnsupportedEdgeSources() {
        var p=basin();
        for(int x=-19;x<=-11;x++)for(int z=-18;z<=-10;z++) {
            assertEquals(IslandPlan.Material.STONE,p.material(x,1,z));
            for(int y=2;y<=7;y++) {
                assertEquals(IslandPlan.Material.WATER,p.material(x,y,z));
                for(var d:List.of(new int[]{-1,0},new int[]{1,0},new int[]{0,-1},new int[]{0,1})) {
                    var c=p.column(x+d[0],z+d[1]);assertNotNull(c);assertTrue(c.bottom()<=y&&c.top()>=y);
                    if(!p.inBasin(x+d[0],z+d[1]))assertEquals(IslandPlan.Material.STONE,p.material(x+d[0],y,z+d[1]));
                }
            }
        }
        assertTrue(p.completeBounds().contains(p.terrainBounds()));assertTrue(p.completeBounds().contains(p.building()));
    }
    @Test void allFourPerimeterDoorSitesHaveDrySolidSupportAndClearSpace() {
        var p=basin();var b=p.building();int mx=(b.minX()+b.maxX())/2,mz=(b.minZ()+b.maxZ())/2;
        for(var site:List.of(new int[]{p.entryX(),p.entryZ()},new int[]{b.minX()-3,mz},new int[]{mx,b.minZ()-3},new int[]{mx,b.maxZ()+3})) {
            assertFalse(p.inBasin(site[0],site[1]));assertNull(p.fluidColumn(site[0],site[1]));
            assertEquals(IslandPlan.Material.STONE,p.material(site[0],p.firstFreeY()-1,site[1]));
            assertEquals(p.firstFreeY()-1,p.solidColumn(site[0],site[1]).top());
            assertFalse(b.contains(site[0],p.firstFreeY(),site[1]));
        }
    }
    @Test void chunkedSolidThenFluidPassPartitionsEveryCellExactlyOnceIncludingNegativeChunks() {
        var p=basin();var seen=new HashSet<IslandCursor.Cell>();int water=0;
        for(boolean fluid:new boolean[]{false,true}) {
            var cursor=new IslandCursor(p,fluid);
            while(cursor.hasNext()) {
                assertTrue(cursor.hasNext());var c=cursor.next();assertTrue(seen.add(c));
                assertEquals(fluid,p.material(c.x(),c.y(),c.z())==IslandPlan.Material.WATER);if(fluid)water++;
            }
            assertFalse(cursor.hasNext());assertThrows(NoSuchElementException.class,cursor::next);
        }
        assertEquals(9*9*6,water);int count=0;var box=p.terrainBounds();
        for(int x=box.minX();x<=box.maxX();x++)for(int z=box.minZ();z<=box.maxZ();z++) {
            var c=p.column(x,z);if(c==null)continue;
            for(int y=c.bottom();y<=c.top();y++) {count++;assertTrue(seen.contains(new IslandCursor.Cell(x,y,z)));}
        }
        assertEquals(count,seen.size());
    }
    @Test void relocatingBasinPreservesDepthFloorAndSafeShore() {
        var p=basin();var moved=p.move(512,31,-1024);
        assertEquals(6,moved.waterDepth());assertEquals(p.completeBounds().move(512,31,-1024),moved.completeBounds());
        assertEquals(p.levels().solidFirstFreeY()+31,moved.levels().solidFirstFreeY());
        assertEquals(p.material(-15,2,-14),moved.material(497,33,-1038));
        assertFalse(moved.inBasin(moved.entryX(),moved.entryZ()));
    }
    @Test void deepWaterAddsToRequiredSpaceAndNeverCropsTheBuildingToFit() {
        var p=new IslandPlan(new BlockBounds(0,0,0,10,10,10),8,4,2,4,IslandPlan.Surface.WATER,64);
        assertEquals(-60,p.bottom());assertEquals(-57,p.solidColumn(0,0).top());
        assertTrue(p.completeBounds().contains(p.building()));
        assertThrows(IllegalArgumentException.class,()->ReservationGeometry.plan(p.completeBounds(),16,0,80));
    }
    @Test void existingDryPresetsKeepTheirHeightAndMaterialAndHaveNoFluidPass() {
        var p=new IslandPlan(basin().building(),8,4,2,4,IslandPlan.Surface.GRASS);
        assertEquals(8,p.levels().solidFirstFreeY());assertFalse(new IslandCursor(p,true).hasNext());
        assertEquals(IslandPlan.Material.GRASS,p.material(0-15,7,-14));
        assertEquals(IslandPlan.Material.DIRT,p.material(-15,5,-14));
        assertEquals(IslandPlan.Material.STONE,p.material(-15,4,-14));
    }
    @Test void missingInvalidOrInconsistentWaterDepthIsRejected() {
        for(int depth:new int[]{0,1,-1,65})assertThrows(IllegalArgumentException.class,
                ()->new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.WATER,depth));
        assertThrows(IllegalArgumentException.class,()->new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.ROCK,8));
        assertThrows(IllegalArgumentException.class,()->new IslandPlan(basin().building(),8,4,2,4,IslandPlan.Surface.WATER));
    }
}
