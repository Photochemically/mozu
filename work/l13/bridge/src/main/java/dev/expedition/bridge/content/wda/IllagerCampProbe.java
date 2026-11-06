package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;

record IllagerCampProbe(ServerLevel world, InteriorBlockProbe floors) implements IllagerCampLayout.Probe {
    IllagerCampProbe(ServerLevel world) { this(world,new InteriorBlockProbe(world)); }
    public Type at(Cell p) { return floors.at(p); }
    private BlockPos pos(Cell p) { return new BlockPos(p.x(),p.y(),p.z()); }
    public boolean dirtPath(Cell p) { return world.getBlockState(pos(p)).is(Blocks.DIRT_PATH); }
    public boolean chest(Cell p) { return world.getBlockState(pos(p)).is(Blocks.CHEST); }
    public boolean signalFire(Cell p) {
        var state=world.getBlockState(pos(p));
        return state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.LIT) && state.getValue(CampfireBlock.SIGNAL_FIRE)
                && state.getFluidState().isEmpty();
    }
}
