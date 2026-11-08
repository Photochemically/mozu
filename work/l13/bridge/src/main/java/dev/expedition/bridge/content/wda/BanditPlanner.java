package dev.expedition.bridge.content.wda;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import java.util.*;

final class BanditPlanner {
    final List<BanditLayout.Feature> features=new ArrayList<>();
    void read(MinecraftServer server,PoolElementStructurePiece piece,CompoundTag element) {
        var template=server.getStructureManager().get(ResourceLocation.parse(element.getString("location"))).orElseThrow();
        var settings=new StructurePlaceSettings().setRotation(piece.getRotation());
        for (var block:List.of(Blocks.CHEST,Blocks.BARREL,Blocks.SPAWNER,Blocks.WATER))
            for(var info:template.filterBlocks(piece.getPosition(),settings,block,true)) {
                boolean water=block==Blocks.WATER;
                if(water&&!info.state().getFluidState().isSource())continue;
                String loot=info.nbt()==null?"":info.nbt().getString("LootTable");
                features.add(new BanditLayout.Feature(BanditLayout.point(info.pos()),BuiltInRegistries.BLOCK.getKey(block).toString(),loot,water));
            }
    }
}
