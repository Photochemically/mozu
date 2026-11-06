package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

record MerchantCampProbe(ServerLevel world, InteriorBlockProbe floors) implements MerchantCampRoute.Probe {
    MerchantCampProbe(ServerLevel world) { this(world, new InteriorBlockProbe(world)); }
    public Type at(Cell p) { return floors.at(p); }
    public boolean chest(Cell p) { return world.getBlockState(new BlockPos(p.x(),p.y(),p.z())).is(Blocks.CHEST); }
    public boolean spruceDoor(Cell p, Direction facing) { return floors.spruceDoor(p,facing); }
}
