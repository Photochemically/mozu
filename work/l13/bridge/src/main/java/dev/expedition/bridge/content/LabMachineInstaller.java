package dev.expedition.bridge.content;

import dev.expedition.bridge.domain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.*;

/**
 * Verifies the actually placed lab/labfinal machine inside the generated instance and derives
 * its exact protection profile. The template-relative profile is anchored to the real piston
 * positions instead of assuming a placement offset, and every component plus its non-air
 * support cell is protected. Failure rejects the room; the whole-room addon was already
 * replaced by this local profile in the bridge-owned definition copy.
 */
public final class LabMachineInstaller {
    private LabMachineInstaller() {}

    public static Set<PortalRecord.Site> installIfLabFinal(ServerLevel world, PortalRecord portal) {
        if (!LabMachineProfile.STRUCTURE_ID.equals(portal.structureId())) return Set.of();
        var destination = portal.destination();
        var bounds = destination.bounds();
        var components = new LinkedHashMap<BlockPos, LabMachineProfile.Component>();
        for (int cx = Math.floorDiv(bounds.minX(), 16); cx <= Math.floorDiv(bounds.maxX(), 16); cx++) {
            for (int cz = Math.floorDiv(bounds.minZ(), 16); cz <= Math.floorDiv(bounds.maxZ(), 16); cz++) {
                var chunk = world.getChunk(cx, cz);
                for (int x = Math.max(chunk.getPos().getMinBlockX(), bounds.minX()); x <= Math.min(chunk.getPos().getMaxBlockX(), bounds.maxX()); x++) {
                    for (int z = Math.max(chunk.getPos().getMinBlockZ(), bounds.minZ()); z <= Math.min(chunk.getPos().getMaxBlockZ(), bounds.maxZ()); z++) {
                        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                            var state = world.getBlockState(new BlockPos(x, y, z));
                            var type = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                            if (!LabMachineProfile.COMPONENT_TYPES.contains(type)) continue;
                            var facing = state.hasProperty(BlockStateProperties.FACING)
                                    ? state.getValue(BlockStateProperties.FACING)
                                    : state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                                            ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : null;
                            components.put(new BlockPos(x, y, z), new LabMachineProfile.Component(type, facing));
                        }
                    }
                }
            }
        }
        var verified = LabMachineProfile.verifyComponents(components);
        var result = new LinkedHashSet<PortalRecord.Site>();
        for (var pos : verified) {
            result.add(site(portal.destination(), pos));
            var below = pos.below();
            if (!world.getBlockState(below).isAir() && NativeRiftPlan.inside(destination, site(portal.destination(), below)))
                result.add(site(portal.destination(), below));
        }
        if (result.size() > PortalRecord.MAX_PROTECTED_BLOCKS)
            throw new IllegalStateException("lab machine protection exceeds the instance limit");
        return Set.copyOf(result);
    }

    private static PortalRecord.Site site(PortalRecord.Destination destination, BlockPos pos) {
        return new PortalRecord.Site(destination.dimension(), pos.getX(), pos.getY(), pos.getZ());
    }
}
