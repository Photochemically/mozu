package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import java.util.*;

final class MonasteryPlanner {
    final List<MonasteryLayout.Socket> sockets=new ArrayList<>();
    final List<MonasteryLayout.Feature> features=new ArrayList<>();
    void read(MinecraftServer server,PoolElementStructurePiece piece,CompoundTag element,int owner){
        var template=server.getStructureManager().get(ResourceLocation.parse(element.getString("location"))).orElseThrow();
        var settings=new StructurePlaceSettings().setRotation(piece.getRotation());
        for(var info:template.filterBlocks(piece.getPosition(),settings,Blocks.JIGSAW,true)){
            var n=info.nbt();if(n==null)throw new IllegalArgumentException("Missing monastery connector data");
            sockets.add(new MonasteryLayout.Socket(owner,info.pos(),JigsawBlock.getFrontFacing(info.state()),JigsawBlock.getTopFacing(info.state()),
                    n.getString("name"),n.getString("target"),n.getString("pool"),n.getString("joint").equals("aligned")));
        }
        for(var block:List.of(Blocks.CHEST,Blocks.BARREL,Blocks.WATER,Blocks.FARMLAND,Blocks.WHEAT,Blocks.CARROTS,Blocks.POTATOES,Blocks.BEETROOTS))for(var info:template.filterBlocks(piece.getPosition(),settings,block,true)){
            var n=info.nbt();if(n==null&&(block==Blocks.CHEST||block==Blocks.BARREL))throw new IllegalArgumentException("Missing monastery container data");var p=info.pos();
            features.add(new MonasteryLayout.Feature(new Cell(p.getX(),p.getY(),p.getZ()),BuiltInRegistries.BLOCK.getKey(block).toString(),n==null?"":n.getString("LootTable"),block==Blocks.WATER?info.state().getValue(LiquidBlock.LEVEL):-1));
        }
    }
}
