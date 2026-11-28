package dev.expedition.bridge.content;

import dev.expedition.bridge.domain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.*;

/**
 * Verifies the actually placed heavy machine inside a generated native room and derives its exact
 * protection profile. Stable components must match the pinned geometry; volatile cells (piston
 * heads, transient moving blocks) are protected when present. Every protected cell plus its
 * non-air support below is journaled; failure rejects the room.
 */
public final class MachineProfileInstaller {
    private MachineProfileInstaller() {}

    public static Set<PortalRecord.Site> install(ServerLevel world, PortalRecord portal) {
        var profile = MachineProfiles.forStructure(world.getServer(), portal.structureId());
        if (profile == null) return Set.of();
        var destination = portal.destination();
        var bounds = destination.bounds();
        var scanned = new LinkedHashSet<MachineProfile.Component>();
        for (int cx = Math.floorDiv(bounds.minX(), 16); cx <= Math.floorDiv(bounds.maxX(), 16); cx++) {
            for (int cz = Math.floorDiv(bounds.minZ(), 16); cz <= Math.floorDiv(bounds.maxZ(), 16); cz++) {
                var chunk = world.getChunk(cx, cz);
                for (int x = Math.max(chunk.getPos().getMinBlockX(), bounds.minX()); x <= Math.min(chunk.getPos().getMaxBlockX(), bounds.maxX()); x++) {
                    for (int z = Math.max(chunk.getPos().getMinBlockZ(), bounds.minZ()); z <= Math.min(chunk.getPos().getMaxBlockZ(), bounds.maxZ()); z++) {
                        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                            var state = world.getBlockState(new BlockPos(x, y, z));
                            var type = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                            if (!profile.allTypes().contains(type)) continue;
                            var facing = state.hasProperty(BlockStateProperties.FACING)
                                    ? state.getValue(BlockStateProperties.FACING)
                                    : state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                                            ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : null;
                            scanned.add(new MachineProfile.Component(type, facing, new BlockPos(x, y, z)));
                        }
                    }
                }
            }
        }
        var verified = profile.verifyComponents(scanned);
        var result = new LinkedHashSet<PortalRecord.Site>();
        for (var pos : verified) protectWithSupport(result, destination, world, pos);
        for (var component : scanned) {
            if (!profile.stableTypes().contains(component.type())) protectWithSupport(result, destination, world, component.pos());
        }
        if (result.size() > PortalRecord.MAX_PROTECTED_BLOCKS)
            throw new IllegalStateException("Machine protection exceeds the instance limit");
        portal.restoreMachineOrigin(site(destination, MachineProfile.minimumCorner(verified)));
        return Set.copyOf(result);
    }

    private static void protectWithSupport(Set<PortalRecord.Site> result, PortalRecord.Destination destination, ServerLevel world, BlockPos pos) {
        result.add(site(destination, pos));
        var below = pos.below();
        if (!world.getBlockState(below).isAir() && NativeRiftPlan.inside(destination, site(destination, below)))
            result.add(site(destination, below));
    }

    private static PortalRecord.Site site(PortalRecord.Destination destination, BlockPos pos) {
        return new PortalRecord.Site(destination.dimension(), pos.getX(), pos.getY(), pos.getZ());
    }
}
