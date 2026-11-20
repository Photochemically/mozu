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

final class ThornbornPlanner {
    final List<ThornbornLayout.Socket> sockets=new ArrayList<>();
    final List<ThornbornLayout.Feature> features=new ArrayList<>();
    void read(MinecraftServer server,PoolElementStructurePiece piece,CompoundTag element,int owner){
        var template=server.getStructureManager().get(ResourceLocation.parse(element.getString("location"))).orElseThrow();
        var settings=new StructurePlaceSettings().setRotation(piece.getRotation());
        for(var info:template.filterBlocks(piece.getPosition(),settings,Blocks.JIGSAW,true)){
            var n=info.nbt();if(n==null)throw new IllegalArgumentException("Missing thornborn connector data");
            sockets.add(new ThornbornLayout.Socket(owner,info.pos(),JigsawBlock.getFrontFacing(info.state()),JigsawBlock.getTopFacing(info.state()),n.getString("name"),n.getString("target"),n.getString("pool"),n.getString("joint").equals("aligned")));
        }
        for(var block:List.of(Blocks.CHEST,Blocks.BARREL,Blocks.SPAWNER))for(var info:template.filterBlocks(piece.getPosition(),settings,block,true)){
            var n=info.nbt();
            if(n==null)throw new IllegalArgumentException("Missing thornborn container or encounter data");var p=info.pos();var id=BuiltInRegistries.BLOCK.getKey(block).toString();
            features.add(new ThornbornLayout.Feature(new Cell(p.getX(),p.getY(),p.getZ()),id,ThornbornLayout.stableData(id,n)));
        }
    }
}
