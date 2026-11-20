package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

record ShirazProbe(ServerLevel world,InteriorBlockProbe blocks) implements ShirazRoute.Probe {
    ShirazProbe(ServerLevel world){this(world,new InteriorBlockProbe(world));}
    private static BlockPos pos(Cell p){return new BlockPos(p.x(),p.y(),p.z());}
    public Type at(Cell p){return blocks.at(p);}
    public boolean stair(Cell p,Direction facing,String shape){var s=world.getBlockState(pos(p));return s.is(Blocks.SMOOTH_SANDSTONE_STAIRS)&&s.getValue(StairBlock.HALF)==Half.BOTTOM
            &&s.getValue(StairBlock.FACING)==facing&&s.getValue(StairBlock.SHAPE).getSerializedName().equals(shape)&&s.getValue(StairBlock.WATERLOGGED);}
    public boolean feature(ShirazLayout.Feature f){var p=pos(f.pos());var s=world.getBlockState(p);if(!BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString().equals(f.block()))return false;
        CompoundTag n;if(s.is(Blocks.WATER)||s.is(Blocks.LAVA)){n=new CompoundTag();n.putInt("level",s.getValue(LiquidBlock.LEVEL));}
        else {var e=world.getBlockEntity(p);if(e==null)return false;n=e.saveWithoutMetadata();}
        return ShirazLayout.stableData(f.block(),n).equals(f.data());
    }
    private static BlockState scaffoldState(int d){if(d<0||d>4)throw new IllegalArgumentException("Unsupported palace bridge span");return Blocks.SCAFFOLDING.defaultBlockState().setValue(ScaffoldingBlock.DISTANCE,d).setValue(ScaffoldingBlock.BOTTOM,d>0).setValue(ScaffoldingBlock.WATERLOGGED,false);}
    public boolean scaffold(Cell p,int d){return world.getBlockState(pos(p)).equals(scaffoldState(d));}
    public void addScaffold(Cell p,int d){if(ScaffoldingBlock.getDistance(world,pos(p))!=d)throw new IllegalStateException("Palace bridge lost support");if(scaffold(p,d))return;
        if(!world.getBlockState(pos(p)).isAir()||!world.setBlock(pos(p),scaffoldState(d),2|16))throw new IllegalStateException("Cannot place palace bridge at "+p);}
}
