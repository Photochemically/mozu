package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

record FishingHutProbe(ServerLevel world, InteriorBlockProbe floors) implements FishingHutRoute.Probe {
    FishingHutProbe(ServerLevel world) { this(world,new InteriorBlockProbe(world)); }
    private static BlockPos pos(Cell p) { return new BlockPos(p.x(),p.y(),p.z()); }
    public Type at(Cell p) { return floors.at(p); }
    public boolean oakPlanks(Cell p) { return world.getBlockState(pos(p)).is(Blocks.OAK_PLANKS); }
    public boolean sprucePile(Cell p) { return world.getBlockState(pos(p)).is(Blocks.SPRUCE_LOG); }
    public boolean barrel(Cell p) { return world.getBlockState(pos(p)).is(Blocks.BARREL); }
    public boolean originalEncounter(Cell socket,boolean hasSpawner) {
        var pos = pos(socket);
        var state = world.getBlockState(pos);
        if (hasSpawner ? !state.is(Blocks.SPAWNER) : !state.isAir()) return false;
        for (int dy=1; dy<=2; dy++) {
            var chain = world.getBlockState(pos.above(dy));
            if (!chain.is(Blocks.CHAIN) || chain.getValue(ChainBlock.AXIS) != Direction.Axis.Y || !chain.getFluidState().isEmpty()) return false;
        }
        return true;
    }
    public boolean waterSource(Cell p) {
        var state = world.getBlockState(pos(p));
        return state.is(Blocks.WATER) && state.getFluidState().isSource();
    }
    public void bridgeSource(Cell p) {
        if (!waterSource(p) || !world.setBlock(pos(p),Blocks.OAK_PLANKS.defaultBlockState(),2|16)) {
            throw new IllegalStateException("Expected a water source in the fishing hut gangway at " + p);
        }
    }
}
