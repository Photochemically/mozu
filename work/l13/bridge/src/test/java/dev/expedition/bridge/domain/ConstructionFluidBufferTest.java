package dev.expedition.bridge.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConstructionFluidBufferTest {
    private static final ConstructionFluidBuffer.Kind WATER=ConstructionFluidBuffer.Kind.WATER,LAVA=ConstructionFluidBuffer.Kind.LAVA;
    private ConstructionFluidBuffer buffer(){return new ConstructionFluidBuffer(new BlockBounds(-16,-5,0,16,15,32),true,false);}
    @Test void onlyConstructionBoundsAreHeldAndDuplicatesRemainOneTick() {
        var b=buffer();assertTrue(b.defer(-16,-5,0,WATER));assertTrue(b.defer(-16,-5,0,WATER));assertEquals(1,b.remaining());
        assertFalse(b.defer(-17,-5,0,WATER));assertFalse(b.defer(0,16,0,WATER));assertFalse(b.defer(0,0,33,WATER));assertFalse(b.defer(0,0,0,LAVA));
    }
    @Test void reschedulingIsBoundedAndReleasedWaterIsNeverHeldAgain() {
        var b=buffer();for(int x=0;x<5;x++)b.defer(x,1,1,WATER);var released=new ArrayList<ConstructionFluidBuffer.Cell>();
        assertThrows(IllegalStateException.class,()->b.release(2,released::add));b.beginRelease();
        assertEquals(2,b.release(2,c->{released.add(c);assertFalse(b.defer(c.x(),c.y(),c.z(),WATER));}));
        assertEquals(3,b.remaining());assertEquals(3,b.release(20,released::add));assertEquals(0,b.release(1,released::add));
        assertEquals(5,new HashSet<>(released).size());assertFalse(b.defer(2,1,1,WATER));
    }
    @Test void aFailedScheduleDoesNotSilentlyDiscardItsTick() {
        var b=buffer();b.defer(0,0,0,WATER);b.beginRelease();
        assertThrows(IllegalArgumentException.class,()->b.release(1,c->{throw new IllegalArgumentException("schedule failed");}));
        assertEquals(1,b.remaining());assertEquals(1,b.release(1,c->{}));
    }
    @Test void wellStagesLavaAndWaterButNotOtherFluidsAndReleaseDoesNotRequeue() {
        var b=new ConstructionFluidBuffer(new BlockBounds(0,0,0,20,30,20),true,true);
        assertTrue(b.defer(2,5,2,LAVA));assertTrue(b.defer(2,5,2,WATER));assertEquals(1,b.remaining());
        assertFalse(b.defer(2,5,2,ConstructionFluidBuffer.Kind.OTHER));assertFalse(b.defer(-1,5,2,LAVA));
        b.beginRelease();assertEquals(1,b.release(10,c->assertFalse(b.defer(c.x(),c.y(),c.z(),LAVA))));
    }
    @Test void dryUnadaptedContentDoesNotPauseAnyFluid() {
        var b=new ConstructionFluidBuffer(new BlockBounds(0,0,0,20,30,20),false,false);
        for(var kind:ConstructionFluidBuffer.Kind.values())assertFalse(b.defer(0,0,0,kind));assertEquals(0,b.remaining());
    }
}
