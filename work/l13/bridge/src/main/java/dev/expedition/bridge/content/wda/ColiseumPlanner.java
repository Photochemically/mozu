package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import java.util.*;

final class ColiseumPlanner {
    final List<ColiseumLayout.Device> devices=new ArrayList<>();
    void read(MinecraftServer server,PoolElementStructurePiece piece,CompoundTag element){
        var template=server.getStructureManager().get(ResourceLocation.parse(element.getString("location"))).orElseThrow();
        var settings=new StructurePlaceSettings().setRotation(piece.getRotation());
        for(var info:template.filterBlocks(piece.getPosition(),settings,Blocks.SPAWNER,true)){
            var n=info.nbt();if(n==null)throw new IllegalArgumentException("Missing original coliseum spawner data");
            var p=info.pos();devices.add(new ColiseumLayout.Device(new Cell(p.getX(),p.getY(),p.getZ()),
                    n.getCompound("SpawnData").copy(),n.getList("SpawnPotentials",Tag.TAG_COMPOUND).copy()));
        }
    }
}
