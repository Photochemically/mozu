package dev.expedition.bridge.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WaterTickBufferTest {
    private WaterTickBuffer buffer(){return new WaterTickBuffer(new BlockBounds(-16,-5,0,16,15,32));}
    @Test void onlyConstructionBoundsAreHeldAndDuplicatesRemainOneTick() {
        var b=buffer();assertTrue(b.defer(-16,-5,0));assertTrue(b.defer(-16,-5,0));assertEquals(1,b.remaining());
        assertFalse(b.defer(-17,-5,0));assertFalse(b.defer(0,16,0));assertFalse(b.defer(0,0,33));
    }
    @Test void reschedulingIsBoundedAndReleasedWaterIsNeverHeldAgain() {
        var b=buffer();for(int x=0;x<5;x++)b.defer(x,1,1);var released=new ArrayList<WaterTickBuffer.Cell>();
        assertThrows(IllegalStateException.class,()->b.release(2,released::add));b.beginRelease();
        assertEquals(2,b.release(2,c->{released.add(c);assertFalse(b.defer(c.x(),c.y(),c.z()));}));
        assertEquals(3,b.remaining());assertEquals(3,b.release(20,released::add));assertEquals(0,b.release(1,released::add));
        assertEquals(5,new HashSet<>(released).size());assertFalse(b.defer(2,1,1));
    }
    @Test void aFailedScheduleDoesNotSilentlyDiscardItsTick() {
        var b=buffer();b.defer(0,0,0);b.beginRelease();
        assertThrows(IllegalArgumentException.class,()->b.release(1,c->{throw new IllegalArgumentException("schedule failed");}));
        assertEquals(1,b.remaining());assertEquals(1,b.release(1,c->{}));
    }
}
