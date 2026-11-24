package dev.expedition.bridge.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/**
 * Short-lived write exemptions for a verified lab machine piston operation. A piston move spans
 * multiple ticks (moving block entities and final completion writes), so a single exact write
 * grant is not enough; positions registered here cover the base, head, the contiguous corridor
 * the moving blocks travel through, and any destroyed or completion cells. Entries expire by
 * game time and are lazily removed; the window is too short to serve as a building exemption.
 */
public final class LabMachineMotion {
    private record Motion(ServerLevel level, UUID portalId, Set<BlockPos> positions, long expiry) {}
    private static final List<Motion> ACTIVE = new ArrayList<>();
    private static final long TICKS = 64;

    private LabMachineMotion() {}

    public static void register(ServerLevel level, UUID portalId, BlockPos base, Direction direction,
                                Collection<BlockPos> moved, Collection<BlockPos> destroyed,
                                Collection<BlockPos> completed) {
        if (level == null || portalId == null || !level.getServer().isSameThread())
            throw new IllegalStateException("Lab machine motion must be registered on the server thread");
        var positions = new HashSet<BlockPos>();
        positions.add(base.immutable());
        positions.add(base.relative(direction));
        // Moving blocks occupy base+dir*(2..N+1) while extending; retraction stays within that span.
        int span = Math.max(moved.size(), 1);
        for (int k = 2; k <= span + 1; k++) positions.add(base.relative(direction, k));
        for (var pos : destroyed) positions.add(pos.immutable());
        for (var pos : completed) positions.add(pos.immutable());
        prune(level);
        ACTIVE.add(new Motion(level, portalId, Set.copyOf(positions), level.getGameTime() + TICKS));
    }

    public static boolean permits(ServerLevel level, BlockPos pos) {
        if (!level.getServer().isSameThread()) return false;
        prune(level);
        for (var motion : ACTIVE)
            if (motion.level() == level && motion.positions().contains(pos)) return true;
        return false;
    }

    private static void prune(ServerLevel level) {
        long now = level.getGameTime();
        ACTIVE.removeIf(motion -> motion.level() == level && motion.expiry() <= now);
    }

    static int activeCount() { return ACTIVE.size(); }

    static void clearForTests() { ACTIVE.clear(); }
}
