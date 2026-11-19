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

final class PlaguePlanner {
    final List<PlagueLayout.Socket> sockets=new ArrayList<>();
    final List<PlagueLayout.Feature> features=new ArrayList<>();
    void read(MinecraftServer server,PoolElementStructurePiece piece,CompoundTag element,int owner) {
        var template=server.getStructureManager().get(ResourceLocation.parse(element.getString("location"))).orElseThrow();
        if(element.getString("location").startsWith(PlagueLayout.PREFIX+"cell_normal_"))PlagueLayout.requireDenseNormalCell(template.save(new CompoundTag()));
        var settings=new StructurePlaceSettings().setRotation(piece.getRotation());
        for(var info:template.filterBlocks(piece.getPosition(),settings,Blocks.JIGSAW,true)) {
            var n=info.nbt();if(n==null)throw new IllegalArgumentException("Missing plague connector data");
            sockets.add(new PlagueLayout.Socket(owner,info.pos(),JigsawBlock.getFrontFacing(info.state()),JigsawBlock.getTopFacing(info.state()),n.getString("name"),n.getString("target"),n.getString("pool"),n.getString("joint").equals("aligned"),n.getString("final_state").equals("minecraft:air")));
        }
        for(var block:List.of(Blocks.CHEST,Blocks.BARREL,Blocks.DISPENSER,Blocks.SPAWNER,Blocks.WATER))
            for(var info:template.filterBlocks(piece.getPosition(),settings,block,true)) {
                var n=info.nbt();
                if(block==Blocks.WATER){n=new CompoundTag();n.putInt("level",info.state().getValue(LiquidBlock.LEVEL));}
                if(n==null)throw new IllegalArgumentException("Missing original plague encounter or loot");
                var p=info.pos();var id=BuiltInRegistries.BLOCK.getKey(block).toString();
                features.add(new PlagueLayout.Feature(owner,new Cell(p.getX(),p.getY(),p.getZ()),id,PlagueLayout.stableData(id,n)));
            }
    }
}
