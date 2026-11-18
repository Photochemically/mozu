package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;

record HindProbe(ServerLevel world,InteriorBlockProbe blocks) implements HindRoute.Probe {
    HindProbe(ServerLevel world){this(world,new InteriorBlockProbe(world));}
    private static BlockPos pos(Cell p){return new BlockPos(p.x(),p.y(),p.z());}
    public Type at(Cell p){var location=pos(p);var state=world.getBlockState(location);
        if(state.is(Blocks.SMOOTH_QUARTZ)&&state.isFaceSturdy(world,location,Direction.UP))return Type.SUPPORT;
        return blocks.at(p);
    }
    public boolean feature(HindLayout.Feature f){
        var p=pos(f.pos());var state=world.getBlockState(p);
        if(!BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(f.block()))return false;
        if(state.is(Blocks.BONE_BLOCK))return state.getValue(BlockStateProperties.AXIS).getName().equals(f.axis());
        if(state.is(Blocks.CHEST)){var entity=world.getBlockEntity(p);return entity!=null&&state.getValue(BlockStateProperties.CHEST_TYPE)==ChestType.SINGLE
                &&!state.getValue(BlockStateProperties.WATERLOGGED)&&state.getValue(BlockStateProperties.HORIZONTAL_FACING).getName().equals(f.facing())
                &&entity.saveWithoutMetadata().getString("LootTable").equals(f.loot());}
        return true;
    }
}
