package dev.expedition.bridge.portal;
import dev.expedition.bridge.domain.BlockBounds;
import net.minecraft.core.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class EntryApproachTest {
    private final BlockPos door=new BlockPos(0,1,0);
    private final BlockBounds bounds=new BlockBounds(-4,0,-4,4,4,4);
    @Test void everyOrientationFacesAwayFromDoorWithSafeForwardSpace() {
        for(var direction:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) {
            var cells=Set.of(door.relative(direction),door.relative(direction,2),door.relative(direction,3));
            var result=EntryApproach.find(door,bounds,cells::contains).orElseThrow();
            assertEquals(door.relative(direction,2),result.feet());assertEquals(direction,result.forward());
        }
    }
    @Test void compactRoomUsesOneStepWithAnotherClearStepAhead() {
        var cells=Set.of(door.east(),door.east(2));
        assertEquals(new EntryApproach.Choice(door.east(),Direction.EAST),EntryApproach.find(door,bounds,cells::contains).orElseThrow());
    }
    @Test void cannotJumpAcrossUnsafeFloorToIsolatedSafeLanding() {
        assertTrue(EntryApproach.find(door,bounds,p->p.equals(door.east(2))||p.equals(door.east(3))).isEmpty());
    }
    @Test void noFallbackOntoDoorOrBeyondReservation() {
        assertTrue(EntryApproach.find(door,bounds,door::equals).isEmpty());
        assertTrue(EntryApproach.find(door,new BlockBounds(0,0,0,0,3,0),p->true).isEmpty());
    }
}
