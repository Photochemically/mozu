package dev.expedition.bridge.portal;

import dev.expedition.bridge.content.MachineProfiles;
import dev.expedition.bridge.domain.LabMachineProfile;
import dev.expedition.bridge.domain.PortalRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * Single decision point for machine piston events. lab/labfinal keeps its pinned two-piston rule;
 * heavy machine rooms use their bundled profiles: the base must be a pinned piston and every
 * protected footprint cell must be a machine component (never a rift, route or support cell).
 */
public final class MachineMotionPolicy {
    private MachineMotionPolicy() {}

    public static boolean permitsMotion(PortalRecord owner, ServerLevel level, BlockPos base, Direction facing,
                                        Collection<BlockPos> footprint, Predicate<BlockPos> isProtected) {
        if (owner == null) return false;
        if (LabMachineProfile.STRUCTURE_ID.equals(owner.structureId()))
            return LabMachineProfile.permitsMotion(owner, base, facing, footprint, List.of(), isProtected);
        var profile = MachineProfiles.forStructure(level.getServer(), owner.structureId());
        if (profile == null) return false;
        return profile.permitsMotion(base, facing, footprint, isProtected,
                pos -> profile.allTypes().contains(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString()));
    }
}
