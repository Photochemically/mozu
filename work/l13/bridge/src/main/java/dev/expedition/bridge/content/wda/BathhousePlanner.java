package dev.expedition.bridge.content.wda;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import java.util.*;

/** Read markers directly from the registered templates, using the native piece's rotation and origin. */
final class BathhousePlanner {
    static final String RULE = """
            {"processor_type":"minecraft:rule","rules":[{
              "location_predicate":{"predicate_type":"minecraft:always_true"},
              "input_predicate":{"predicate_type":"minecraft:random_block_match","block":"minecraft:calcite","probability":0.1},
              "output_state":{"Name":"minecraft:diorite"}}]}
            """;
    final Set<BlockPos> sockets = new HashSet<>();
    final List<BlockPos> waterSources = new ArrayList<>();

    BathhousePlanner(RegistryAccess registries) {
        var lists = registries.registryOrThrow(Registries.PROCESSOR_LIST);
        var empty = Objects.requireNonNull(lists.get(ResourceLocation.parse("minecraft:empty")));
        var main = Objects.requireNonNull(lists.get(ResourceLocation.parse("dungeons_arise:bathhouse_main")));
        if (!empty.list().isEmpty() || main.list().size() != 1) throw new IllegalArgumentException("Unexpected bathhouse processors");
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        var codec = StructureProcessorType.SINGLE_CODEC;
        var expected = codec.parse(ops, JsonParser.parseString(RULE)).result().orElseThrow();
        // Compare canonical encodings so optional default predicates are treated consistently.
        if (!codec.encodeStart(ops, expected).result().orElseThrow().equals(
                codec.encodeStart(ops, main.list().get(0)).result().orElseThrow())) {
            throw new IllegalArgumentException("Bathhouse finish must only substitute calcite with diorite");
        }
    }

    void read(MinecraftServer server, PoolElementStructurePiece piece, CompoundTag element) {
        String id = element.getString("location");
        if (piece.getElement().getClass() != SinglePoolElement.class ||
                !element.getString("processors").equals(BathhouseLayout.processors(id))) {
            throw new IllegalArgumentException("Unsupported bathhouse element " + id);
        }
        var origin = piece.getPosition();
        var rotation = piece.getRotation();
        var template = server.getStructureManager().get(ResourceLocation.parse(id)).orElseThrow();
        var settings = new StructurePlaceSettings().setRotation(rotation);
        for (var block : template.filterBlocks(origin, settings, Blocks.WATER, true)) {
            if (block.state().getFluidState().isSource()) waterSources.add(block.pos());
        }
        for (var joint : piece.getElement().getShuffledJigsawBlocks(server.getStructureManager(), origin, rotation, RandomSource.create(0))) {
            var tag = joint.nbt();
            if (JigsawBlock.getFrontFacing(joint.state()) == Direction.UP && tag != null
                    && tag.getString("target").equals("dungeons_arise:bathhouse_spawner_0")
                    && tag.getString("pool").equals("dungeons_arise:bathhouse/bathhouse_spawners")) {
                sockets.add(joint.pos());
            }
        }
    }
}
