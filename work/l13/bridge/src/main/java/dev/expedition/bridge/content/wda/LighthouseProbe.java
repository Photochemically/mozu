package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;

final class LighthouseProbe implements LighthouseRoute.Probe {
    private final ServerLevel world;
    private final InteriorBlockProbe floors;
    LighthouseProbe(ServerLevel world) {
        this.world = world;
        floors = new InteriorBlockProbe(world);
    }
    public InteriorBlockRules.Type at(Cell p) { return floors.at(p); }

    public boolean originalStep(Cell p, int turns) {
        var state = world.getBlockState(new BlockPos(p.x(), p.y(), p.z()));
        return state.is(Blocks.STONE_BRICK_STAIRS) && state.getFluidState().isEmpty()
                && state.getValue(StairBlock.HALF) == Half.BOTTOM && state.getValue(StairBlock.SHAPE) == StairsShape.STRAIGHT
                && state.getValue(StairBlock.FACING) == Direction.from2DDataValue(turns);
    }

    public boolean originalLadder(Cell p, int turns) {
        var pos = new BlockPos(p.x(), p.y(), p.z());
        var state = world.getBlockState(pos);
        return state.is(Blocks.LADDER) && state.getFluidState().isEmpty() && state.canSurvive(world, pos)
                && state.getValue(LadderBlock.FACING) == Direction.from2DDataValue(turns).getOpposite();
    }
}
