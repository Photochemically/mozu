package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

record WindmillProbe(ServerLevel world, InteriorBlockProbe floors) implements WindmillRoute.Probe {
    WindmillProbe(ServerLevel world) { this(world, new InteriorBlockProbe(world)); }
    public Type at(Cell p) { return floors.at(p); }
    public boolean originalStep(Cell p, Direction facing, boolean andesite) {
        var state = world.getBlockState(new BlockPos(p.x(), p.y(), p.z()));
        return state.is(andesite ? Blocks.ANDESITE_STAIRS : Blocks.COBBLESTONE_STAIRS) && state.getFluidState().isEmpty()
                && state.getValue(StairBlock.HALF) == Half.BOTTOM && state.getValue(StairBlock.SHAPE) == StairsShape.STRAIGHT
                && state.getValue(StairBlock.FACING) == facing;
    }
}
