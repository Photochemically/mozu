package dev.expedition.bridge.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pure motion window geometry; the runtime registry itself needs a live server thread. */
class TrustedMachineMotionTest {
    private static final BlockPos BASE = new BlockPos(10, 91, 58);

    @Test void bareExtensionCoversBaseHeadAndFirstTargetCell() {
        var positions = TrustedMachineMotion.motionPositions(BASE, Direction.EAST, List.of(), List.of(), List.of());
        assertEquals(Set.of(BASE, BASE.east(), BASE.east(2)), positions);
    }

    @Test void oneBlockRetractionStaysInsideTheHeadSpan() {
        var positions = TrustedMachineMotion.motionPositions(BASE, Direction.UP,
                List.of(BASE.above(2)), List.of(), List.of());
        assertEquals(Set.of(BASE, BASE.above(), BASE.above(2)), positions);
    }

    @Test void fullTwelveBlockPushCoversTheWholeCorridor() {
        var moved = new ArrayList<BlockPos>();
        for (int k = 2; k <= 13; k++) moved.add(BASE.east(k));
        var positions = TrustedMachineMotion.motionPositions(BASE, Direction.EAST, moved, List.of(), List.of());
        var expected = new HashSet<BlockPos>();
        expected.add(BASE);
        for (int k = 1; k <= 13; k++) expected.add(BASE.east(k));
        assertEquals(expected, positions);
    }

    @Test void destroyedAndCompletionCellsJoinTheWindow() {
        var positions = TrustedMachineMotion.motionPositions(BASE, Direction.UP,
                List.of(), List.of(BASE.above(3)), List.of(BASE.above(2)));
        assertTrue(positions.contains(BASE.above(3)));
        assertTrue(positions.contains(BASE.above(2)));
    }

    @Test void motionSetsAreImmutableAndNeverContainTheOppositeSide() {
        var positions = TrustedMachineMotion.motionPositions(BASE, Direction.EAST, List.of(), List.of(), List.of());
        assertThrows(UnsupportedOperationException.class, () -> positions.add(BASE.west()));
        assertFalse(positions.contains(BASE.west()), "retraction direction cells stay outside the extension corridor");
    }
}
