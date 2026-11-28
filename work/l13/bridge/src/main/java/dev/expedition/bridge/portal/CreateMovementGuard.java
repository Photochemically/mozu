package dev.expedition.bridge.portal;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult;
import net.minecraft.server.level.ServerLevel;

/**
 * Optional Create hook. Registers a movement-allowed check that refuses assembly while any
 * protected device position is inside the captured structure, so a contraption can never be
 * created from protected blocks. Create raises AssemblyException.unmovableBlock during capture,
 * before any block is removed from the world or a moving entity is spawned.
 *
 * Create is optional: a missing or incompatible Create runtime simply leaves the guard inactive;
 * the chunk-write guard still covers relocation side effects.
 */
public final class CreateMovementGuard {
    private static boolean registered;

    private CreateMovementGuard() {}

    public static void register() {
        if (registered) return;
        try {
            BlockMovementChecks.registerMovementAllowedCheck((state, level, pos) ->
                    decide(level instanceof ServerLevel serverLevel && ManagedDoorProtection.protectedAt(serverLevel, pos)));
            registered = true;
        } catch (LinkageError missing) {
            // Optional dependency absent; keep the guard inactive without failing bridge loading.
        }
    }

    /** Pure decision so tests can cover it without a running Create runtime. */
    public static CheckResult decide(boolean protectedPosition) {
        return protectedPosition ? CheckResult.FAIL : CheckResult.PASS;
    }
}
