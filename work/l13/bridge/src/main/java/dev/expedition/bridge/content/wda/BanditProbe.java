package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

record BanditProbe(ServerLevel world,InteriorBlockProbe floors) implements BanditRoute.Probe {
    BanditProbe(ServerLevel world){this(world,new InteriorBlockProbe(world));}
    private static BlockPos pos(Cell p){return new BlockPos(p.x(),p.y(),p.z());}
    public Type at(Cell p) {
        if(!world.getBlockState(pos(p)).is(Blocks.RED_SAND))return floors.at(p);
        // Falling sand is only a footing if the entire column has a stable base.
        for(int depth=1;depth<=64;depth++) {
            var below=p.move(0,-depth,0);
            if(!world.getBlockState(pos(below)).is(Blocks.RED_SAND))return floors.at(below)==Type.SUPPORT?Type.SUPPORT:Type.BLOCKED;
        }
        return Type.BLOCKED;
    }
    public boolean stair(Cell p,String kind,Direction facing) {
        var state=world.getBlockState(pos(p));
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals("minecraft:"+kind)
                &&state.getValue(StairBlock.HALF)==Half.BOTTOM&&state.getValue(StairBlock.SHAPE)==StairsShape.STRAIGHT
                &&state.getValue(StairBlock.FACING)==facing&&state.getFluidState().isEmpty();
    }
    public boolean feature(BanditLayout.Feature f) {
        var p=pos(f.pos());var state=world.getBlockState(p);
        if(!BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(f.block()))return false;
        if(f.source())return state.getFluidState().isSource();
        if(f.block().equals("minecraft:spawner"))return world.getBlockEntity(p) instanceof SpawnerBlockEntity;
        return world.getBlockEntity(p) instanceof RandomizableContainerBlockEntity container
                &&container.saveWithoutMetadata().getString("LootTable").equals(f.loot());
    }
    public boolean stepBlock(Cell p){return world.getBlockState(pos(p)).is(Blocks.SMOOTH_SANDSTONE);}
    public void addStep(Cell p) {
        if(!world.getBlockState(pos(p)).isAir())throw new IllegalStateException("Bandit step destination changed");
        world.setBlock(pos(p),Blocks.SMOOTH_SANDSTONE.defaultBlockState(),3);
    }
}
