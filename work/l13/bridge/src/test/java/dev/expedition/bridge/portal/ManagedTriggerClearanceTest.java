package dev.expedition.bridge.portal;

import dev.expedition.bridge.domain.BlockBounds;
import net.minecraft.core.*;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ManagedTriggerClearanceTest {
    private AABB body(double x,double y,double z) {return new AABB(x-0.3,y,z-0.3,x+0.3,y+1.8,z+0.3);}
    @Test void smallRoomApproachesAreAlreadyClearInAllFourDirections() {
        var door=new BlockPos(754,1,260);var bounds=new BlockBounds(750,0,256,770,4,265);
        var trigger=Set.of(door,door.above());
        for(var direction:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) {
            // Even the compact one-step approach must not require walking farther into the room.
            var choice=EntryApproach.find(door,bounds,Set.of(door.relative(direction),door.relative(direction,2))::contains).orElseThrow();
            var p=choice.feet();assertTrue(ManagedTriggerClearance.outside(body(p.getX()+0.5,p.getY(),p.getZ()+0.5),trigger::contains));
        }
    }
    @Test void bodyEdgeStillInsideDoorAndUpperHalfCannotRelease() {
        var trigger=Set.of(new BlockPos(0,1,0),new BlockPos(0,2,0));
        assertFalse(ManagedTriggerClearance.outside(body(1.1,1,0.5),trigger::contains));
        assertFalse(ManagedTriggerClearance.outside(body(0.5,2.5,0.5),trigger::contains));
        assertTrue(ManagedTriggerClearance.outside(body(1.31,1,0.5),trigger::contains));
    }
    @Test void exactBlockBoundaryDoesNotCountAsOverlapIncludingNegativeCoordinates() {
        var trigger=Set.of(new BlockPos(-1,1,-1),new BlockPos(-1,2,-1));
        assertTrue(ManagedTriggerClearance.outside(new AABB(0,1,-1,0.6,2.8,0),trigger::contains));
        assertTrue(ManagedTriggerClearance.outside(new AABB(-1.6,1,-1,-1,2.8,0),trigger::contains));
        assertFalse(ManagedTriggerClearance.outside(new AABB(-0.001,1,-1,0.599,2.8,0),trigger::contains));
    }
    @Test void predicateOnlyInspectsOccupiedBodyCellsNotFarawayLandingRadius() {
        var checked=new HashSet<BlockPos>();
        assertTrue(ManagedTriggerClearance.outside(body(100.5,20,200.5),p->{checked.add(p.immutable());return false;}));
        assertEquals(Set.of(new BlockPos(100,20,200),new BlockPos(100,21,200)),checked);
    }
}
