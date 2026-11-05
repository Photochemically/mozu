package dev.expedition.bridge.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReturnAreaTest {
    @Test void negativeCoordinatesUseFloorDivisionAndExactlyNineChunks() {
        var area=ReturnArea.around(new PortalRecord.Site("minecraft:overworld",-1,70,-17));
        assertEquals(new ReturnArea(-32,-48,15,-1),area);
        assertEquals(48,area.maxX()-area.minX()+1);assertEquals(48,area.maxZ()-area.minZ()+1);
        assertTrue(area.contains(-32,-48));assertTrue(area.contains(15,-1));assertFalse(area.contains(16,-1));
    }
    @Test void allDoorPositionsWithinTheSameChunkShareTheSameReturnArea() {
        assertEquals(ReturnArea.around(new PortalRecord.Site("minecraft:overworld",0,64,0)),
                ReturnArea.around(new PortalRecord.Site("minecraft:overworld",15,90,15)));
        assertNotEquals(ReturnArea.around(new PortalRecord.Site("minecraft:overworld",15,64,0)),
                ReturnArea.around(new PortalRecord.Site("minecraft:overworld",16,64,0)));
    }
}
