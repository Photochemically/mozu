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
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.*;
import java.util.*;

final class LighthousePlanner {
    static LighthouseLayout prepare(MinecraftServer server, StructureStart start, int surface, BlockBounds complete) {
        var parts = new ArrayList<LighthouseLayout.Piece>();
        for (var raw : start.getPieces()) {
            var pool = (PoolElementStructurePiece) raw;
            var registries = server.registryAccess();
            var encoded = StructurePoolElement.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries),
                    pool.getElement()).getOrThrow(false, message -> { throw new IllegalArgumentException(message); });
            if (!(encoded instanceof CompoundTag tag) || !tag.contains("location", Tag.TAG_STRING)
                    || !tag.getString("processors").equals("minecraft:empty")
                    || pool.getElement().getClass() != SinglePoolElement.class
                    || pool.getElement().getProjection() != StructureTemplatePool.Projection.RIGID
                    || !registries.registryOrThrow(Registries.PROCESSOR_LIST).get(ResourceLocation.parse("minecraft:empty")).list().isEmpty()) {
                throw new IllegalArgumentException("Lighthouse requires its original rigid, unprocessed templates");
            }
            var pos = pool.getPosition();
            var origin = new Cell(pos.getX(), pos.getY(), pos.getZ());
            var rotation = pool.getRotation();
            int turns = switch (rotation) {
                case NONE -> 0;
                case CLOCKWISE_90 -> 1;
                case CLOCKWISE_180 -> 2;
                case COUNTERCLOCKWISE_90 -> 3;
            };
            var transform = new LighthouseLayout(origin, turns);
            var id = tag.getString("location");
            boolean lower = LighthouseLayout.BASE.equals(id);
            var expected = transform.at(15, lower ? 30 : 0, 20);
            var joints = pool.getElement().getShuffledJigsawBlocks(server.getStructureManager(), pos, rotation, RandomSource.create(0));
            if (joints.size() != 1) throw new IllegalArgumentException("Lighthouse needs one joint per piece");
            var joint = joints.get(0);
            var nbt = joint.nbt();
            if (!joint.pos().equals(new BlockPos(expected.x(), expected.y(), expected.z()))
                    || JigsawBlock.getFrontFacing(joint.state()) != (lower ? Direction.UP : Direction.DOWN)
                    || JigsawBlock.getTopFacing(joint.state()) != rotation.rotate(Direction.SOUTH)
                    || nbt == null || !nbt.getString("name").equals(LighthouseLayout.TARGET)
                    || !nbt.getString("target").equals(LighthouseLayout.TARGET)
                    || !nbt.getString("pool").equals("dungeons_arise:enigmatic/lighthouse/lighthouse_tower")
                    || !nbt.getString("joint").equals("aligned") || !nbt.getString("final_state").equals("minecraft:structure_void")) {
                throw new IllegalArgumentException("Changed lighthouse tower connection: " + id);
            }
            parts.add(new LighthouseLayout.Piece(id, origin, turns, WdaStructurePlan.bounds(raw.getBoundingBox())));
        }
        return LighthouseLayout.inspect(parts, surface, complete);
    }
}
