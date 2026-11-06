package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BlockBounds;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.DungeonSpec;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import java.util.List;
import java.util.Set;

/** Rigid windmill tower and its native terrain-matching field attachments. */
record WindmillLayout(BlockPos origin, Rotation rotation) {
    static final String TARGET = "dungeons_arise:illager_windmill";
    static final String PREFIX = TARGET + "/illager_windmill_";
    private static final Set<String> FIELDS = Set.of("field_0", "field_1", "field_2", "field_3", "field_4");
    private static final Set<String> MOBS = Set.of("pillager_0", "vindicator_1", "witch_2");
    record Piece(String id, BlockPos origin, Rotation rotation, BlockBounds bounds, boolean terrainMatching) {}

    static WindmillLayout fromRoot(StructureStart start) {
        var root = (PoolElementStructurePiece) start.getPieces().get(0);
        return new WindmillLayout(root.getPosition(), root.getRotation());
    }

    static void validate(List<Piece> pieces, WdaStructurePlanner.Environment env) {
        if (env.entryMode() != DungeonSpec.EntryMode.PERIMETER || env.boarding()
                || (env.surface() != IslandPlan.Surface.GRASS && env.surface() != IslandPlan.Surface.ROCK))
            throw new IllegalArgumentException("Windmill needs dry ground and perimeter exits");
        if (pieces.isEmpty()) throw new IllegalArgumentException("Missing windmill root");
        var root = pieces.get(0);
        if (!root.id().equals(PREFIX + "part_0") || root.origin().getY() + 3 != env.firstFreeY())
            throw new IllegalArgumentException("Windmill stairs do not meet the planned surface");
        int baseCount = 0, upperCount = 0;
        for (var p : pieces) {
            if (!p.id().startsWith(PREFIX)) throw new IllegalArgumentException("Unexpected windmill piece " + p.id());
            var id = p.id().substring(PREFIX.length());
            if (FIELDS.contains(id) != p.terrainMatching())
                throw new IllegalArgumentException("Changed windmill piece projection " + id);
            if (FIELDS.contains(id) || MOBS.contains(id)) continue;
            var expected = switch (id) {
                case "part_0" -> { baseCount++; yield root.origin(); }
                case "part_1" -> { upperCount++; yield root.origin().above(32); }
                default -> throw new IllegalArgumentException("Unknown windmill template " + id);
            };
            var end = new BlockPos(47, 31, 33).rotate(root.rotation()).offset(expected);
            var bounds = new BlockBounds(Math.min(expected.getX(), end.getX()), expected.getY(), Math.min(expected.getZ(), end.getZ()),
                    Math.max(expected.getX(), end.getX()), end.getY(), Math.max(expected.getZ(), end.getZ()));
            if (!expected.equals(p.origin()) || root.rotation() != p.rotation() || !bounds.equals(p.bounds()))
                throw new IllegalArgumentException("Detached or resized windmill tower " + id);
        }
        if (baseCount != 1 || upperCount != 1) throw new IllegalArgumentException("Windmill needs both original tower halves");
    }

    Cell at(int x, int y, int z) {
        var p = new BlockPos(x, y, z).rotate(rotation).offset(origin);
        return new Cell(p.getX(), p.getY(), p.getZ());
    }
}
