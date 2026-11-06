package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import java.util.*;

final class TyphonDivePlanner {
    static TyphonDiveLayout prepare(MinecraftServer server, StructureStart start, IslandPlan island) {
        var pieces = new ArrayList<TyphonDiveLayout.Piece>();
        for (var raw : start.getPieces()) {
            // The common WDA planner has already validated the pool element and processor registry entry.
            var pool = (PoolElementStructurePiece) raw;
            var registries = server.registryAccess();
            var encoded = StructurePoolElement.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries),
                    pool.getElement()).getOrThrow(false, message -> { throw new IllegalArgumentException(message); });
            if (!(encoded instanceof CompoundTag tag) || !tag.contains("location", Tag.TAG_STRING)
                    || !tag.getString("processors").equals("minecraft:empty")
                    || pool.getElement().getProjection() != StructureTemplatePool.Projection.RIGID
                    || !registries.registryOrThrow(Registries.PROCESSOR_LIST)
                            .get(ResourceLocation.parse("minecraft:empty")).list().isEmpty()) {
                throw new IllegalArgumentException("Typhon requires original rigid pieces and empty processors");
            }
            var templateId = tag.getString("location");
            var template = server.getStructureManager().get(ResourceLocation.parse(templateId)).orElseThrow();
            var rotation = pool.getRotation();
            var origin = pool.getPosition();
            var settings = new StructurePlaceSettings().setRotation(rotation);
            var magma = template.filterBlocks(origin, settings, Blocks.MAGMA_BLOCK, true).stream()
                    .map(info -> cell(info.pos())).toList();
            var chests = new ArrayList<Cell>();
            for (var chest : template.filterBlocks(origin, settings, Blocks.CHEST, true)) {
                if (chest.nbt() == null || !chest.nbt().getString("LootTable").equals("dungeons_arise:chests/typhon/typhon_treasure")) {
                    throw new IllegalArgumentException("Changed original Typhon treasure table");
                }
                chests.add(cell(chest.pos()));
            }
            int turns = switch (rotation) {
                case NONE -> 0;
                case CLOCKWISE_90 -> 1;
                case CLOCKWISE_180 -> 2;
                case COUNTERCLOCKWISE_90 -> 3;
            };
            var piece = new TyphonDiveLayout.Piece(templateId, cell(origin), turns,
                    WdaStructurePlan.bounds(raw.getBoundingBox()), magma, chests);
            var joints = pool.getElement().getShuffledJigsawBlocks(server.getStructureManager(), origin,
                    rotation, RandomSource.create(0));
            validateJoints(piece, joints, rotation);
            pieces.add(piece);
        }
        return TyphonDiveLayout.fromPieces(pieces, island);
    }

    private static Cell cell(BlockPos position) {
        return new Cell(position.getX(), position.getY(), position.getZ());
    }

    private static void validateJoints(TyphonDiveLayout.Piece piece,
                                       List<StructureTemplate.StructureBlockInfo> joints, Rotation rotation) {
        var expected = TyphonDiveLayout.joints(piece.template());
        if (joints.size() != expected.size()) throw new IllegalArgumentException("Changed Typhon native connections");
        for (var joint : expected) {
            var position = piece.at(joint.x(), 0, 0);
            var facing = rotation.rotate(Objects.requireNonNull(Direction.byName(joint.front())));
            long matches = joints.stream().filter(info -> {
                var tag = info.nbt();
                return position.equals(cell(info.pos())) && JigsawBlock.getFrontFacing(info.state()) == facing
                        && JigsawBlock.getTopFacing(info.state()) == Direction.UP && tag != null
                        && tag.getString("name").equals("dungeons_arise:typhon_side_" + joint.name())
                        && tag.getString("target").equals("dungeons_arise:typhon_side_" + joint.target())
                        && tag.getString("pool").equals("dungeons_arise:aquatic/typhon/typhon_main")
                        && tag.getString("joint").equals("rollable")
                        && tag.getString("final_state").equals("minecraft:structure_void");
            }).count();
            if (matches != 1) throw new IllegalArgumentException("Changed or missing Typhon joint " + joint.name());
        }
    }
}
