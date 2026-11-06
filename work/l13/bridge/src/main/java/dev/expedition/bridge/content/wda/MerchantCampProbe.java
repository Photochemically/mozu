package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

record MerchantCampProbe(ServerLevel world, InteriorBlockProbe floors) implements MerchantCampRoute.Probe {
    MerchantCampProbe(ServerLevel world) { this(world, new InteriorBlockProbe(world)); }
    public Type at(Cell p) { return floors.at(p); }
    public boolean chest(Cell p) { return world.getBlockState(new BlockPos(p.x(),p.y(),p.z())).is(Blocks.CHEST); }
    public boolean spruceDoor(Cell p, Direction facing) {
        var pos = new BlockPos(p.x(),p.y(),p.z());
        var lower = world.getBlockState(pos);
        var upper = world.getBlockState(pos.above());
        return lower.is(Blocks.SPRUCE_DOOR) && lower.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER
                && lower.getValue(DoorBlock.FACING)==facing && lower.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER).equals(upper);
    }
}
