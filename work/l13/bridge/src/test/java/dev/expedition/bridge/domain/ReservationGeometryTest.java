package dev.expedition.bridge.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReservationGeometryTest {
    @Test void negativePieceOffsetsAndMarginsAreIncluded() {
        var plan=ReservationGeometry.plan(new BlockBounds(-90,-12,-40,150,100,85),3,0,256);
        var moved=plan.translatedContent(1024,-2048);
        assertEquals(new BlockBounds(1027,3,-2045,1267,115,-1920),moved);
        assertEquals(247,plan.padded().width());
        assertEquals(132,plan.padded().depth());
    }
    @Test void tallStructuresFailBeforeAllocationAndExactHeightFits() {
        assertThrows(IllegalArgumentException.class,()->ReservationGeometry.plan(new BlockBounds(0,0,0,15,255,15),1,0,256));
        assertEquals(256,ReservationGeometry.plan(new BlockBounds(0,0,0,15,255,15),0,0,256).padded().height());
    }
}
