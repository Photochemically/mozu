package dev.expedition.bridge.portal;

import net.minecraft.core.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlockMutationFootprintTest {
    @Test void stickyRetractionIncludesInFlightCompletionEvenWithNoResolvedPull() {
        var base=new BlockPos(-4,50,-9);
        for(var facing:Direction.values()) {
            var pending=base.relative(facing,2);
            assertEquals(Set.of(base,base.relative(facing),pending),
                    BlockMutationFootprint.piston(base,facing,false,List.of(),List.of(),List.of(pending)));
        }
    }
    @Test void extensionChecksMovedDestinationsAndDestroyedBlocksInEveryDirection() {
        var base=new BlockPos(-20,50,-20);
        for(var direction:Direction.values()) {
            var source=base.relative(direction,2);var destroyed=base.relative(direction,5);
            var set=BlockMutationFootprint.piston(base,direction,true,List.of(source),List.of(destroyed));
            assertEquals(Set.of(base,base.relative(direction),source,source.relative(direction),destroyed),set);
            assertFalse(set.contains(base.relative(direction,4)));
        }
    }
    @Test void stickyRetractionChecksTheOppositeDestinationAndEmptyMovesStillIncludeTheHead() {
        var base=new BlockPos(5,50,5);
        for(var direction:Direction.values()) {
            var source=base.relative(direction,3);
            assertEquals(Set.of(base,base.relative(direction),source,base.relative(direction,2)),
                    BlockMutationFootprint.piston(base,direction,false,List.of(source),List.of()));
            assertEquals(Set.of(base,base.relative(direction)),BlockMutationFootprint.piston(base,direction,false,List.of(),List.of()));
        }
    }
    @Test void mutableInputPositionsCannotChangeTheReviewedFootprint() {
        var base=new BlockPos.MutableBlockPos(1,2,3);var moved=new BlockPos.MutableBlockPos(2,2,3);
        var footprint=BlockMutationFootprint.piston(base,Direction.EAST,true,List.of(moved),List.of());
        base.set(100,100,100);moved.set(200,200,200);
        assertEquals(Set.of(new BlockPos(1,2,3),new BlockPos(2,2,3),new BlockPos(3,2,3)),footprint);
        assertThrows(UnsupportedOperationException.class,footprint::clear);
    }
}
