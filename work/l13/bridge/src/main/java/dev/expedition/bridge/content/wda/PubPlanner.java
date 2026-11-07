package dev.expedition.bridge.content.wda;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import java.util.*;

final class PubPlanner {
    static final String PROCESSORS="dungeons_arise:greenwood_pub_main";
    static final String RULE="""
            {"processor_type":"minecraft:rule","rules":[
             {"location_predicate":{"predicate_type":"minecraft:always_true"},
              "input_predicate":{"predicate_type":"minecraft:block_match","block":"minecraft:red_stained_glass"},
              "output_state":{"Name":"minecraft:structure_void"}},
             {"location_predicate":{"predicate_type":"minecraft:always_true"},
              "input_predicate":{"predicate_type":"minecraft:random_block_match","block":"minecraft:mud_bricks","probability":0.2},
              "output_state":{"Name":"minecraft:packed_mud"}},
             {"location_predicate":{"predicate_type":"minecraft:always_true"},
              "input_predicate":{"predicate_type":"minecraft:random_block_match","block":"minecraft:stone_bricks","probability":0.15},
              "output_state":{"Name":"minecraft:mossy_stone_bricks"}}]}
            """;
    final List<PubLayout.Loot> loot=new ArrayList<>();
    PubPlanner(RegistryAccess registries) {
        var list=registries.registryOrThrow(Registries.PROCESSOR_LIST).get(ResourceLocation.parse(PROCESSORS));
        if (list==null || list.list().size()!=1) throw new IllegalArgumentException("Changed pub processors");
        var ops=RegistryOps.create(JsonOps.INSTANCE,registries);
        var codec=StructureProcessorType.SINGLE_CODEC;
        var expected=codec.parse(ops,JsonParser.parseString(RULE)).result().orElseThrow();
        if (!codec.encodeStart(ops,expected).result().orElseThrow().equals(codec.encodeStart(ops,list.list().get(0)).result().orElseThrow()))
            throw new IllegalArgumentException("Unsupported pub material or marker rule");
    }
    void read(MinecraftServer server,PoolElementStructurePiece piece,CompoundTag element) {
        if (piece.getElement().getClass()!=SinglePoolElement.class || !element.getString("processors").equals(PROCESSORS))
            throw new IllegalArgumentException("Unexpected pub element");
        var template=server.getStructureManager().get(ResourceLocation.parse(element.getString("location"))).orElseThrow();
        var settings=new StructurePlaceSettings().setRotation(piece.getRotation());
        // The pinned original templates have no red-glass markers. Require a new route audit if that changes.
        if (!template.filterBlocks(piece.getPosition(),settings,Blocks.RED_STAINED_GLASS,true).isEmpty())
            throw new IllegalArgumentException("New pub placeholder cells require a placement audit");
        for (var kind:List.of(Blocks.CHEST,Blocks.BARREL)) for (var block:template.filterBlocks(piece.getPosition(),settings,kind,true)) {
            if (block.nbt()==null || !block.nbt().contains("LootTable")) continue;
            loot.add(new PubLayout.Loot(block.pos(),kind==Blocks.CHEST?"minecraft:chest":"minecraft:barrel",block.nbt().getString("LootTable")));
        }
    }
}
