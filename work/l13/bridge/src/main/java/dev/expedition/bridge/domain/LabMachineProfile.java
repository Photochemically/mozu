package dev.expedition.bridge.domain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.*;

/**
 * Pinned mechanical profile of the original dimdoors lab/labfinal template (24x108x88).
 * Replaces the whole-room prevent_block_modification addon with exact device protection plus
 * trusted piston motion for the room's own two sticky pistons.
 *
 * All coordinates below are template-relative; the installer anchors them to the actually
 * placed machine instead of trusting a fixed origin offset.
 */
public final class LabMachineProfile {
    public static final String STRUCTURE_ID = "lab/labfinal";
    /** Original template fingerprint; profile must be re-reviewed if the source changes. */
    public static final String TEMPLATE_SHA256 = "ae9b9cb6fad915cef3430779e5e4049cbb0b8e16d6a8e0842e3da478ecd93d8d";

    public record Component(String type, Direction facing) {
        public Component { Objects.requireNonNull(type); }
        public static Component of(String type) { return new Component(type, null); }
    }

    private static final String STICKY_PISTON = "minecraft:sticky_piston";
    private static final String PISTON_HEAD = "minecraft:piston_head";
    private static final String SLIME_BLOCK = "minecraft:slime_block";
    private static final String LEVER = "minecraft:lever";
    private static final String REPEATER = "minecraft:repeater";
    private static final String REDSTONE_WIRE = "minecraft:redstone_wire";

    public static final Set<String> COMPONENT_TYPES = Set.of(
            STICKY_PISTON, PISTON_HEAD, SLIME_BLOCK, LEVER, REPEATER, REDSTONE_WIRE);

    /** P1 base (12,90,58), facing UP, slime on its head. */
    private static final BlockPos P1 = new BlockPos(12, 90, 58);
    /** P2 base = P1 + (-2,1,0), facing EAST. */
    private static final BlockPos P2_OFFSET = new BlockPos(-2, 1, 0);
    /** Wire offsets relative to P1, 17 cells. */
    private static final List<BlockPos> WIRES = List.of(
            new BlockPos(0, 0, 1), new BlockPos(0, 0, 2), new BlockPos(2, 0, 2),
            new BlockPos(-5, 1, 0), new BlockPos(-4, 1, 0), new BlockPos(-3, 1, 0),
            new BlockPos(-5, 1, 2), new BlockPos(-4, 1, 2), new BlockPos(-3, 1, 2),
            new BlockPos(-3, 1, 3), new BlockPos(2, 1, 3),
            new BlockPos(-2, 2, 3), new BlockPos(-2, 2, 4), new BlockPos(-1, 2, 4),
            new BlockPos(0, 2, 4), new BlockPos(1, 2, 4), new BlockPos(2, 2, 4));
    private static final BlockPos LEVER_OFFSET = new BlockPos(-1, 4, 4);
    private static final BlockPos REPEATER_A_OFFSET = new BlockPos(1, 0, 2);
    private static final BlockPos REPEATER_B_OFFSET = new BlockPos(-5, 1, 1);
    private static final int COMPONENT_COUNT = 25;

    private LabMachineProfile() {}

    /**
     * Verifies a component map against the pinned profile. The map keys are template-relative
     * positions and values are block types with required orientation. Returns the verified
     * component positions when the complete machine matches, otherwise throws.
     */
    public static Set<BlockPos> verifyComponents(Map<BlockPos, Component> components) {
        Objects.requireNonNull(components, "components");
        if (components.size() != COMPONENT_COUNT)
            throw new IllegalArgumentException("lab machine component count mismatch: " + components.size());
        var pistons = new LinkedHashMap<Direction, BlockPos>();
        for (var entry : components.entrySet()) {
            if (entry.getValue().type().equals(STICKY_PISTON)) {
                var facing = entry.getValue().facing();
                if (facing == null || pistons.putIfAbsent(facing, entry.getKey().immutable()) != null)
                    throw new IllegalArgumentException("lab machine piston orientation is not unique or missing: " + entry.getKey());
            }
        }
        if (!pistons.keySet().equals(Set.of(Direction.UP, Direction.EAST)))
            throw new IllegalArgumentException("lab machine needs exactly one up and one east sticky piston");
        var p1 = Objects.requireNonNull(pistons.get(Direction.UP));
        var p2 = Objects.requireNonNull(pistons.get(Direction.EAST));
        if (!p2.equals(p1.offset(P2_OFFSET)))
            throw new IllegalArgumentException("lab machine piston geometry changed");
        require(components, p1.offset(0, 1, 0), PISTON_HEAD, Direction.UP);
        require(components, p2.offset(1, 0, 0), PISTON_HEAD, Direction.EAST);
        require(components, p1.offset(0, 2, 0), SLIME_BLOCK, null);
        require(components, p1.offset(LEVER_OFFSET), LEVER, null);
        require(components, p1.offset(REPEATER_A_OFFSET), REPEATER, Direction.EAST);
        require(components, p1.offset(REPEATER_B_OFFSET), REPEATER, Direction.SOUTH);
        for (var wire : WIRES) require(components, p1.offset(wire), REDSTONE_WIRE, null);
        var verified = new LinkedHashSet<BlockPos>();
        for (var pos : components.keySet()) verified.add(pos.immutable());
        return Set.copyOf(verified);
    }

    private static void require(Map<BlockPos, Component> components, BlockPos pos, String type, Direction facing) {
        var component = components.get(pos);
        if (component == null || !component.type().equals(type)
                || (facing != null && component.facing() != facing))
            throw new IllegalArgumentException("lab machine component mismatch at " + pos + ": expected " + type
                    + (facing == null ? "" : " facing " + facing) + ", got " + component);
    }

    /**
     * Decides whether a piston event can run on a lab machine piston. Only the machine's own
     * base, head and (for the up-facing piston) its slime payload may be touched when they are
     * protected; any other protected position in the move/destroy sets keeps the motion blocked.
     */
    public static boolean permitsMotion(PortalRecord owner, BlockPos base, Direction facing,
                                        Collection<BlockPos> moved, Collection<BlockPos> destroyed,
                                        java.util.function.Predicate<BlockPos> isProtected) {
        if (owner == null || !STRUCTURE_ID.equals(owner.structureId())) return false;
        if (facing != Direction.UP && facing != Direction.EAST) return false;
        var head = base.relative(facing);
        var allowed = new HashSet<BlockPos>();
        allowed.add(base.immutable());
        allowed.add(head.immutable());
        if (facing == Direction.UP) allowed.add(head.relative(facing)); // original slime payload
        for (var pos : moved)
            if (isProtected.test(pos) && !allowed.contains(pos)) return false;
        for (var pos : destroyed)
            if (isProtected.test(pos) && !allowed.contains(pos)) return false;
        return true;
    }
}
