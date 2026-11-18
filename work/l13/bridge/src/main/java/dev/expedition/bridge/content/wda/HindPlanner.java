package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import java.util.*;

final class HindPlanner {
    final List<HindLayout.Feature> features=new ArrayList<>();
    void read(MinecraftServer server,PoolElementStructurePiece piece,CompoundTag element){
        var template=server.getStructureManager().get(ResourceLocation.parse(element.getString("location"))).orElseThrow();
        var settings=new StructurePlaceSettings().setRotation(piece.getRotation());
        for(var block:List.of(Blocks.BONE_BLOCK,Blocks.SMOOTH_QUARTZ,Blocks.GOLD_BLOCK,Blocks.CHEST,Blocks.AIR))
            for(var info:template.filterBlocks(piece.getPosition(),settings,block,true)){
                var n=info.nbt();if(block==Blocks.CHEST&&n==null)throw new IllegalArgumentException("Missing original fossil treasure data");
                var p=info.pos();var state=info.state();
                features.add(new HindLayout.Feature(new Cell(p.getX(),p.getY(),p.getZ()),BuiltInRegistries.BLOCK.getKey(block).toString(),
                        block==Blocks.BONE_BLOCK?state.getValue(BlockStateProperties.AXIS).getName():"",
                        block==Blocks.CHEST?state.getValue(BlockStateProperties.HORIZONTAL_FACING).getName():"",n==null?"":n.getString("LootTable")));
            }
    }
}
