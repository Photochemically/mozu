package dev.expedition.bridge.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureIndexGridTest {
    @Test void oversizedStructuresKeepEverySavedReferenceWithinVanillaReloadLimit() {
        var grid=new StructureIndexGrid(-35,-47,44,31);
        for(int x=-35;x<=44;x++)for(int z=-47;z<=31;z++) {
            var anchor=grid.anchorFor(x,z);
            assertTrue(Math.abs(anchor.x()-x)<=8 && Math.abs(anchor.z()-z)<=8);
            assertTrue(anchor.x()>=-35 && anchor.x()<=44 && anchor.z()>=-47 && anchor.z()<=31);
            assertEquals(anchor,grid.anchorFor(anchor.x(),anchor.z()));
        }
    }
    @Test void singleChunkAndFinalPartialRegionStayWithinOwnedChunks() {
        assertEquals(new StructureIndexGrid.Anchor(-1,3),new StructureIndexGrid(-1,3,-1,3).anchorFor(-1,3));
        var grid=new StructureIndexGrid(-20,-20,-4,-4);
        assertEquals(new StructureIndexGrid.Anchor(-4,-4),grid.anchorFor(-4,-4));
        assertThrows(IllegalArgumentException.class,()->grid.anchorFor(-3,-4));
    }
}
