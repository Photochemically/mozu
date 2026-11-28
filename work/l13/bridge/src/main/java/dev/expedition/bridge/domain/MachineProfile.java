package dev.expedition.bridge.domain;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.*;
import java.util.function.Predicate;

/**
 * Canonical heavy machine geometry pinned from an original dimdoors template (.schem).
 * Stable components (bases, wiring, torches, repeaters, sensors, doors, ...) are verified exactly;
 * volatile cells (piston heads, transient moving blocks) are protected when present but never
 * break verification, because redstone can react during chunk placement.
 */
public final class MachineProfile {
    public record Component(String type, Direction facing, BlockPos pos) {
        public Component { Objects.requireNonNull(type); Objects.requireNonNull(pos); }
        public static Component of(String type, Direction facing, int x, int y, int z) {
            return new Component(type, facing, new BlockPos(x, y, z));
        }
    }
    private static final Set<String> PISTON_TYPES = Set.of("minecraft:sticky_piston", "minecraft:piston");

    private final String structureId;
    private final String templateSha256;
    private final List<Integer> size;
    private final Set<String> stableTypes;
    private final Set<String> volatileTypes;
    private final Set<String> allTypes;
    private final Set<Component> stable; // normalized: min corner of the machine is (0,0,0)
    private final Set<BlockPos> stablePositions;
    private final Map<BlockPos, Direction> pistonBases;

    private MachineProfile(String structureId, String templateSha256, List<Integer> size,
                           Set<String> stableTypes, Set<String> volatileTypes,
                           Set<Component> stable, Set<BlockPos> stablePositions, Map<BlockPos, Direction> pistonBases) {
        this.structureId = structureId;
        this.templateSha256 = templateSha256;
        this.size = List.copyOf(size);
        this.stableTypes = Set.copyOf(stableTypes);
        this.volatileTypes = Set.copyOf(volatileTypes);
        var all = new LinkedHashSet<String>(stableTypes);
        all.addAll(volatileTypes);
        this.allTypes = Set.copyOf(all);
        this.stable = Set.copyOf(stable);
        this.stablePositions = Set.copyOf(stablePositions);
        this.pistonBases = Map.copyOf(pistonBases);
    }

    public String structureId() { return structureId; }
    public String templateSha256() { return templateSha256; }
    public List<Integer> size() { return size; }
    public Set<String> stableTypes() { return stableTypes; }
    public Set<String> allTypes() { return allTypes; }
    public int stableCount() { return stable.size(); }
    public int pistonBaseCount() { return pistonBases.size(); }
    public Set<BlockPos> stablePositions() { return stablePositions; }
    public Set<Component> stableComponents() { return stable; }
    public Map<BlockPos, Direction> pistonBases() { return pistonBases; }

    /** Parses and validates a bundled profile resource. Never trusts the file: geometry is re-derived and checked. */
    public static MachineProfile fromJson(String structureId, JsonObject json) {
        if (structureId == null || !structureId.matches("[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Invalid machine profile id");
        if (json.get("schema") == null || json.get("schema").getAsInt() != 1) throw new IllegalArgumentException("Unsupported machine profile schema");
        if (!structureId.equals(json.get("id").getAsString()))
            throw new IllegalArgumentException("Machine profile id mismatch");
        var sha = json.get("template_sha256").getAsString();
        if (!sha.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid machine template fingerprint");
        var sizeArr = json.getAsJsonArray("size");
        if (sizeArr.size() != 3) throw new IllegalArgumentException("Invalid machine template size");
        var size = List.of(sizeArr.get(0).getAsInt(), sizeArr.get(1).getAsInt(), sizeArr.get(2).getAsInt());
        if (size.stream().anyMatch(s -> s <= 0)) throw new IllegalArgumentException("Invalid machine template size");
        var stableTypes = new LinkedHashSet<String>();
        for (var element : json.getAsJsonArray("component_types")) stableTypes.add(element.getAsString());
        if (stableTypes.isEmpty()) throw new IllegalArgumentException("Machine profile has no component types");
        var volatileTypes = new LinkedHashSet<String>();
        for (var element : json.getAsJsonArray("volatile_types")) volatileTypes.add(element.getAsString());

        var stable = new LinkedHashSet<Component>();
        var min = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        for (var element : json.getAsJsonArray("stable")) {
            var component = readComponent(element.getAsJsonObject(), stableTypes);
            if (!stable.add(component)) throw new IllegalArgumentException("Duplicate machine component: " + component);
            min[0] = Math.min(min[0], component.pos().getX());
            min[1] = Math.min(min[1], component.pos().getY());
            min[2] = Math.min(min[2], component.pos().getZ());
        }
        if (stable.isEmpty()) throw new IllegalArgumentException("Machine profile has no stable components");
        // Normalize once: the machine's own minimum corner becomes (0,0,0), independent of template placement.
        var normalized = new LinkedHashSet<Component>();
        var positions = new LinkedHashSet<BlockPos>();
        for (var component : stable) {
            var shifted = new Component(component.type(), component.facing(),
                    new BlockPos(component.pos().getX() - min[0], component.pos().getY() - min[1], component.pos().getZ() - min[2]));
            normalized.add(shifted);
            positions.add(shifted.pos().immutable());
        }
        var bases = new LinkedHashMap<BlockPos, Direction>();
        for (var element : json.getAsJsonArray("piston_bases")) {
            var pos = new BlockPos(element.getAsJsonObject().getAsJsonArray("pos").get(0).getAsInt() - min[0],
                    element.getAsJsonObject().getAsJsonArray("pos").get(1).getAsInt() - min[1],
                    element.getAsJsonObject().getAsJsonArray("pos").get(2).getAsInt() - min[2]);
            var facing = readFacing(element.getAsJsonObject().get("facing").getAsString());
            var pistonPresent = PISTON_TYPES.stream().anyMatch(t -> normalized.contains(new Component(t, facing, pos)));
            if (!pistonPresent)
                throw new IllegalArgumentException("Machine piston base is not a stable piston component: " + pos + " " + facing);
            if (bases.putIfAbsent(pos.immutable(), facing) != null)
                throw new IllegalArgumentException("Duplicate machine piston base");
        }
        if (bases.isEmpty()) throw new IllegalArgumentException("Machine profile has no piston bases");
        return new MachineProfile(structureId, sha, size, stableTypes, volatileTypes, normalized, positions, bases);
    }

    private static Component readComponent(JsonObject element, Set<String> stableTypes) {
        var type = element.get("type").getAsString();
        if (!stableTypes.contains(type)) throw new IllegalArgumentException("Machine component outside declared types: " + type);
        var posArr = element.getAsJsonArray("pos");
        var facing = element.has("facing") && !element.get("facing").isJsonNull() ? readFacing(element.get("facing").getAsString()) : null;
        return new Component(type, facing, new BlockPos(posArr.get(0).getAsInt(), posArr.get(1).getAsInt(), posArr.get(2).getAsInt()));
    }

    private static Direction readFacing(String facing) {
        return switch (facing) {
            case "UP" -> Direction.UP;
            case "DOWN" -> Direction.DOWN;
            case "NORTH" -> Direction.NORTH;
            case "SOUTH" -> Direction.SOUTH;
            case "EAST" -> Direction.EAST;
            case "WEST" -> Direction.WEST;
            default -> throw new IllegalArgumentException("Invalid machine facing: " + facing);
        };
    }

    /**
     * Verifies an actually placed machine against the pinned geometry. Scanned volatile cells are
     * ignored for equality (they legitimately change during placement); the stable set must match
     * exactly after translation by each side's minimum corner. Returns the verified stable world positions.
     */
    public Set<BlockPos> verifyComponents(Collection<Component> scanned) {
        Objects.requireNonNull(scanned, "scanned");
        var worldStable = new LinkedHashSet<Component>();
        var min = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        for (var component : scanned) {
            if (!stableTypes.contains(component.type())) continue;
            if (!worldStable.add(component)) throw new IllegalArgumentException("Duplicate placed machine component: " + component);
            min[0] = Math.min(min[0], component.pos().getX());
            min[1] = Math.min(min[1], component.pos().getY());
            min[2] = Math.min(min[2], component.pos().getZ());
        }
        if (worldStable.size() != stable.size())
            throw new IllegalArgumentException("Machine component count mismatch: " + worldStable.size() + " != " + stable.size());
        var normalized = new LinkedHashSet<Component>();
        var positions = new LinkedHashSet<BlockPos>();
        for (var component : worldStable) {
            var shifted = new Component(component.type(), component.facing(),
                    new BlockPos(component.pos().getX() - min[0], component.pos().getY() - min[1], component.pos().getZ() - min[2]));
            normalized.add(shifted);
            positions.add(component.pos().immutable());
        }
        if (!normalized.equals(stable))
            throw new IllegalArgumentException("Placed machine geometry does not match the pinned profile");
        return Set.copyOf(positions);
    }

    /**
     * A piston event is the machine's own motion only when its base is a pinned piston with the
     * pinned facing, and every protected cell in the footprint is a machine component (never a
     * rift, route endpoint, support cell or structural block).
     */
    public boolean permitsMotion(BlockPos base, Direction facing, Collection<BlockPos> footprint,
                                 Predicate<BlockPos> isProtected, Predicate<BlockPos> componentAt) {
        var pinned = pistonBases.get(base);
        if (pinned == null || pinned != facing) return false;
        for (var pos : footprint)
            if (isProtected.test(pos) && !componentAt.test(pos)) return false;
        return true;
    }

    public static Set<String> pistonTypes() { return PISTON_TYPES; }
}
