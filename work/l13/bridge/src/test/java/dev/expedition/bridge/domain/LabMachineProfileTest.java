package dev.expedition.bridge.domain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pinned machine geometry and trusted-motion rules; no world or game instance involved. */
class LabMachineProfileTest {
    private static final BlockPos P1 = new BlockPos(12, 90, 58);
    private static final BlockPos P2 = new BlockPos(10, 91, 58);

    private Map<BlockPos, LabMachineProfile.Component> fullMachine() {
        var map = new LinkedHashMap<BlockPos, LabMachineProfile.Component>();
        map.put(P1, new LabMachineProfile.Component("minecraft:sticky_piston", Direction.UP));
        map.put(P2, new LabMachineProfile.Component("minecraft:sticky_piston", Direction.EAST));
        map.put(P1.above(), new LabMachineProfile.Component("minecraft:piston_head", Direction.UP));
        map.put(P2.east(), new LabMachineProfile.Component("minecraft:piston_head", Direction.EAST));
        map.put(P1.above(2), LabMachineProfile.Component.of("minecraft:slime_block"));
        map.put(P1.offset(-1, 4, 4), LabMachineProfile.Component.of("minecraft:lever"));
        map.put(P1.offset(1, 0, 2), new LabMachineProfile.Component("minecraft:repeater", Direction.EAST));
        map.put(P1.offset(-5, 1, 1), new LabMachineProfile.Component("minecraft:repeater", Direction.SOUTH));
        for (var wire : List.of(
                new BlockPos(0, 0, 1), new BlockPos(0, 0, 2), new BlockPos(2, 0, 2),
                new BlockPos(-5, 1, 0), new BlockPos(-4, 1, 0), new BlockPos(-3, 1, 0),
                new BlockPos(-5, 1, 2), new BlockPos(-4, 1, 2), new BlockPos(-3, 1, 2),
                new BlockPos(-3, 1, 3), new BlockPos(2, 1, 3),
                new BlockPos(-2, 2, 3), new BlockPos(-2, 2, 4), new BlockPos(-1, 2, 4),
                new BlockPos(0, 2, 4), new BlockPos(1, 2, 4), new BlockPos(2, 2, 4)))
            map.put(P1.offset(wire), LabMachineProfile.Component.of("minecraft:redstone_wire"));
        return map;
    }

    @Test void completePinnedMachineVerifiesAgainstTheProfile() {
        var verified = LabMachineProfile.verifyComponents(fullMachine());
        assertEquals(25, verified.size());
        assertTrue(verified.contains(P1) && verified.contains(P2) && verified.contains(P1.above(2)));
    }

    @Test void shiftedMachineStillAnchorsToTheRealPistons() {
        var shift = new BlockPos(300, -40, 700);
        var shifted = new LinkedHashMap<BlockPos, LabMachineProfile.Component>();
        for (var entry : fullMachine().entrySet())
            shifted.put(entry.getKey().offset(shift), entry.getValue());
        assertEquals(25, LabMachineProfile.verifyComponents(shifted).size());
    }

    @Test void missingSwappedOrMisalignedComponentsRefuseVerification() {
        var noSlime = fullMachine();
        noSlime.remove(P1.above(2));
        assertThrows(IllegalArgumentException.class, () -> LabMachineProfile.verifyComponents(noSlime));
        var wrongHead = fullMachine();
        wrongHead.put(P1.above(), new LabMachineProfile.Component("minecraft:piston_head", Direction.EAST));
        assertThrows(IllegalArgumentException.class, () -> LabMachineProfile.verifyComponents(wrongHead));
        var swapped = fullMachine();
        swapped.put(P2, new LabMachineProfile.Component("minecraft:sticky_piston", Direction.UP));
        assertThrows(IllegalArgumentException.class, () -> LabMachineProfile.verifyComponents(swapped));
        var movedPiston = fullMachine();
        movedPiston.put(P2, new LabMachineProfile.Component("minecraft:sticky_piston", Direction.EAST));
        movedPiston.put(P1.offset(-2, 2, 0), new LabMachineProfile.Component("minecraft:sticky_piston", Direction.EAST));
        assertThrows(IllegalArgumentException.class, () -> LabMachineProfile.verifyComponents(movedPiston));
        var extra = fullMachine();
        extra.put(P1.offset(5, 5, 5), LabMachineProfile.Component.of("minecraft:redstone_wire"));
        assertThrows(IllegalArgumentException.class, () -> LabMachineProfile.verifyComponents(extra));
    }

    private PortalRecord labOwner() {
        var record = new PortalRecord(UUID.randomUUID(), PortalKind.EXPLORATION, 3,
                new PortalRecord.Site("minecraft:overworld", 0, 70, 0), 1L);
        record.preparationStarted(LabMachineProfile.STRUCTURE_ID);
        return record;
    }

    @Test void ownPistonMayRetractItsSlimePayloadAndMoveItsHead() {
        var owner = labOwner();
        var protectedBlocks = Set.of(P1, P1.above(), P1.above(2));
        var isProtected = (java.util.function.Predicate<BlockPos>) protectedBlocks::contains;
        // P1 retracts the slime from above the head back onto the head cell.
        assertTrue(LabMachineProfile.permitsMotion(owner, P1, Direction.UP, List.of(P1.above(2)), List.of(), isProtected));
        // P1 extension pushes the slime off the head.
        assertTrue(LabMachineProfile.permitsMotion(owner, P1, Direction.UP, List.of(P1.above(2)), List.of(), isProtected));
    }

    @Test void protectedForeignBlocksInsideTheMoveStayBlocked() {
        var owner = labOwner();
        var protectedBlocks = Set.of(P1, P1.above(), P1.above(2), P1.above(3));
        var isProtected = (java.util.function.Predicate<BlockPos>) protectedBlocks::contains;
        // P1 extension would touch a protected cell beyond its own payload: refused.
        assertFalse(LabMachineProfile.permitsMotion(owner, P1, Direction.UP, List.of(P1.above(3)), List.of(), isProtected));
        assertFalse(LabMachineProfile.permitsMotion(owner, P1, Direction.UP, List.of(), List.of(P1.above(3)), isProtected));
    }

    @Test void eastPistonCannotDragTheUpPistonsHeadOrRunOutsideTheRoomProfile() {
        var owner = labOwner();
        var protectedBlocks = Set.of(P2, P2.east(), P1.above());
        var isProtected = (java.util.function.Predicate<BlockPos>) protectedBlocks::contains;
        // P2 extension would touch P1's head cell: not part of P2's own device, refused.
        assertFalse(LabMachineProfile.permitsMotion(owner, P2, Direction.EAST, List.of(P1.above()), List.of(), isProtected));
        // Other owners, non-lab rooms and arbitrary directions never grant motion.
        assertFalse(LabMachineProfile.permitsMotion(null, P1, Direction.UP, List.of(), List.of(), p -> false));
        var other = new PortalRecord(UUID.randomUUID(), PortalKind.EXPLORATION, 3,
                new PortalRecord.Site("minecraft:overworld", 0, 70, 0), 1L);
        other.preparationStarted("myth/eden");
        assertFalse(LabMachineProfile.permitsMotion(other, P1, Direction.UP, List.of(), List.of(), p -> false));
        assertFalse(LabMachineProfile.permitsMotion(owner, P1, Direction.DOWN, List.of(), List.of(), p -> false));
    }
}
