package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

record TreeHouseProbe(ServerLevel world,InteriorBlockProbe floors) implements TreeHouseRoute.Probe {
    TreeHouseProbe(ServerLevel world) { this(world,new InteriorBlockProbe(world)); }
    public Type at(Cell p) { return floors.at(p); }
    public boolean originalSupport(Cell p) {
        var pos=new BlockPos(p.x(),p.y(),p.z()); var state=world.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) return false;
        if (state.is(Blocks.JUNGLE_LEAVES)) return state.getValue(LeavesBlock.PERSISTENT);
        return state.is(Blocks.JUNGLE_WOOD) || state.is(Blocks.BONE_BLOCK) || state.is(Blocks.DARK_OAK_PLANKS);
    }
    public boolean loot(BlockPos pos,String kind,String table) {
        var expected=kind.equals("barrel")?Blocks.BARREL:Blocks.CHEST;
        return world.getBlockState(pos).is(expected) && world.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container
                && table.equals(container.saveWithoutMetadata().getString("LootTable"));
    }
    public boolean ladder(BlockPos pos,Direction facing) {
        var state=world.getBlockState(pos);
        return state.is(Blocks.LADDER) && state.getValue(LadderBlock.FACING)==facing
                && state.getFluidState().isEmpty() && state.canSurvive(world,pos);
    }
}
