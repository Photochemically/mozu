package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

record MonasteryProbe(ServerLevel world,InteriorBlockProbe blocks) implements MonasteryRoute.Probe {
    MonasteryProbe(ServerLevel world){this(world,new InteriorBlockProbe(world));}
    private static BlockPos pos(Cell p){return new BlockPos(p.x(),p.y(),p.z());}
    public Type at(Cell p){
        var location=pos(p);var state=world.getBlockState(location);
        var id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if(state.getFluidState().isEmpty()&&!state.hasBlockEntity()){
            if(MonasteryRoute.gardenPlant(id)&&state.getCollisionShape(world,location).isEmpty())return Type.PASSABLE;
            // Vanilla farmland has a full-width 15/16-high floor; two clear cells above still fit a player.
            if(state.is(Blocks.FARMLAND))return Type.SUPPORT;
        }
        return blocks.at(p);
    }
    public boolean window(Cell p,Direction facing){var s=world.getBlockState(pos(p));return s.is(Blocks.SPRUCE_STAIRS)&&s.getValue(StairBlock.HALF)==Half.TOP
            &&s.getValue(StairBlock.FACING)==facing&&s.getValue(StairBlock.SHAPE)==StairsShape.STRAIGHT&&s.getFluidState().isEmpty();}
    public boolean feature(MonasteryLayout.Feature f){
        var p=pos(f.pos());var state=world.getBlockState(p);
        if(!BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(f.block()))return false;
        if(state.is(Blocks.WATER))return state.getValue(LiquidBlock.LEVEL)==f.waterLevel();
        if(state.is(Blocks.CHEST)||state.is(Blocks.BARREL)){var entity=world.getBlockEntity(p);return entity!=null&&entity.saveWithoutMetadata().getString("LootTable").equals(f.loot());}
        return true;
    }
    private static BlockState scaffoldState(int distance){if(distance<0||distance>1)throw new IllegalArgumentException("Unsupported monastery scaffold span");
        return Blocks.SCAFFOLDING.defaultBlockState().setValue(ScaffoldingBlock.DISTANCE,distance).setValue(ScaffoldingBlock.BOTTOM,distance==1).setValue(ScaffoldingBlock.WATERLOGGED,false);}
    public boolean scaffold(Cell p,int distance){return world.getBlockState(pos(p)).equals(scaffoldState(distance));}
    public void addScaffold(Cell p,int distance){
        if(ScaffoldingBlock.getDistance(world,pos(p))!=distance)throw new IllegalStateException("Monastery scaffold lost its support");
        if(scaffold(p,distance))return;
        if(!world.getBlockState(pos(p)).isAir()||!world.setBlock(pos(p),scaffoldState(distance),2|16))throw new IllegalStateException("Cannot place monastery scaffold at "+p);
    }
}
