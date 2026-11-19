package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

record InfestedProbe(ServerLevel world,InteriorBlockProbe blocks) implements InfestedRoute.Probe {
    InfestedProbe(ServerLevel world){this(world,new InteriorBlockProbe(world));}
    private static BlockPos pos(Cell p){return new BlockPos(p.x(),p.y(),p.z());}
    public Type at(Cell p){return blocks.at(p);}
    public boolean stair(Cell p,Direction facing){var s=world.getBlockState(pos(p));return s.is(Blocks.SPRUCE_STAIRS)&&s.getValue(StairBlock.FACING)==facing
            &&s.getValue(StairBlock.HALF)==Half.BOTTOM&&s.getValue(StairBlock.SHAPE)==StairsShape.STRAIGHT&&s.getFluidState().isEmpty();}
    public boolean feature(InfestedLayout.Feature f){var p=pos(f.pos());var entity=world.getBlockEntity(p);return entity!=null
            &&BuiltInRegistries.BLOCK.getKey(world.getBlockState(p).getBlock()).toString().equals(f.block())
            &&InfestedLayout.stableData(f.block(),entity.saveWithoutMetadata()).equals(f.data());}
}
