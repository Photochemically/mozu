package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import java.util.*;

final class InfestedPlanner {
    final List<InfestedLayout.Socket> sockets=new ArrayList<>();
    final List<InfestedLayout.Feature> features=new ArrayList<>();
    void read(MinecraftServer server,PoolElementStructurePiece piece,CompoundTag element,int owner){
        var template=server.getStructureManager().get(ResourceLocation.parse(element.getString("location"))).orElseThrow();
        var settings=new StructurePlaceSettings().setRotation(piece.getRotation());
        for(var info:template.filterBlocks(piece.getPosition(),settings,Blocks.JIGSAW,true)){
            var n=info.nbt();if(n==null)throw new IllegalArgumentException("Missing infested connector data");
            sockets.add(new InfestedLayout.Socket(owner,info.pos(),JigsawBlock.getFrontFacing(info.state()),JigsawBlock.getTopFacing(info.state()),n.getString("name"),n.getString("target"),n.getString("pool"),n.getString("joint").equals("aligned")));
        }
        for(var block:List.of(Blocks.CHEST,Blocks.BARREL,Blocks.SPAWNER))for(var info:template.filterBlocks(piece.getPosition(),settings,block,true)){
            var n=info.nbt();if(n==null)throw new IllegalArgumentException("Missing infested container or encounter data");var p=info.pos();var id=BuiltInRegistries.BLOCK.getKey(block).toString();
            features.add(new InfestedLayout.Feature(new Cell(p.getX(),p.getY(),p.getZ()),id,InfestedLayout.stableData(id,n)));
        }
    }
}
