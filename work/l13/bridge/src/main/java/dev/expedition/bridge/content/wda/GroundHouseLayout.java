package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BlockBounds;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.DungeonSpec;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Local coordinates are relative to the original floor piece, including after relocation. */
record GroundHouseLayout(Variant variant, BlockPos origin, Rotation rotation) implements PerimeterLayout {
    enum Variant { CLEAN, RUINS, MUSHROOM }

    static final String PRAIRIE = "dungeons_arise:small_prairie_house";
    static final String MUSHROOM = "dungeons_arise:mushroom_house";
    private static final String PRAIRIE_PIECE = PRAIRIE + "/small_prairie_house_";
    private static final String MUSHROOM_PIECE = MUSHROOM + "/mushroom_house_";
    private static final String ROOTS = MUSHROOM_PIECE + "roots_0";
    private static final Set<String> SPAWNERS = Set.of(
            MUSHROOM_PIECE + "piglin_0", MUSHROOM_PIECE + "piglin_1", MUSHROOM_PIECE + "piglin_2");

    // Each original up-facing spawner socket has an aligned joint. Its top fixes the child's rotation.
    private record Socket(int x, int y, int z, Direction top) {}
    private static final Map<String,List<Socket>> FLOORS = Map.of(
            MUSHROOM_PIECE + "bottom_0", List.of(new Socket(7,1,5,Direction.NORTH)),
            MUSHROOM_PIECE + "bottom_1", List.of(),
            MUSHROOM_PIECE + "bottom_2", List.of(new Socket(9,1,4,Direction.EAST)),
            MUSHROOM_PIECE + "bottom_3", List.of());
    private static final Map<String,List<Socket>> CAPS = Map.of(
            MUSHROOM_PIECE + "top_0", List.of(new Socket(8,3,15,Direction.SOUTH), new Socket(21,3,15,Direction.EAST),
                    new Socket(19,20,15,Direction.WEST)),
            MUSHROOM_PIECE + "top_1", List.of(new Socket(15,5,20,Direction.NORTH)),
            MUSHROOM_PIECE + "top_2", List.of(new Socket(18,0,15,Direction.EAST)),
            MUSHROOM_PIECE + "top_3", List.of(new Socket(15,4,17,Direction.WEST), new Socket(19,9,15,Direction.SOUTH)),
            MUSHROOM_PIECE + "top_4", List.of(new Socket(15,0,18,Direction.NORTH)));

    static GroundHouseLayout inspect(String target, List<WdaTemplatePiece> pieces, WdaStructurePlanner.Environment env) {
        if (env.entryMode() != DungeonSpec.EntryMode.PERIMETER || env.boarding()
                || (env.surface() != IslandPlan.Surface.GRASS && env.surface() != IslandPlan.Surface.ROCK)) {
            throw new IllegalArgumentException("Ground houses require a dry perimeter");
        }
        if (pieces.isEmpty() || pieces.stream().anyMatch(WdaTemplatePiece::terrainMatching)) {
            throw new IllegalArgumentException("Missing rigid house structure");
        }
        var root = pieces.get(0);
        if (root.origin().getY() + 1 != env.firstFreeY()) {
            throw new IllegalArgumentException("House floor does not meet the surface");
        }
        if (target.equals(PRAIRIE)) return prairie(root, pieces);
        if (target.equals(MUSHROOM) && FLOORS.containsKey(root.id())) return mushroom(root, pieces);
        throw new IllegalArgumentException("Unknown ground house root " + root.id());
    }

    private static GroundHouseLayout prairie(WdaTemplatePiece root, List<WdaTemplatePiece> pieces) {
        String kind;
        if (root.id().equals(PRAIRIE_PIECE + "clean_0_floor")) kind = "clean";
        else if (root.id().equals(PRAIRIE_PIECE + "ruins_0_floor")) kind = "ruins";
        else throw new IllegalArgumentException("Unknown prairie house floor");

        if (pieces.size() != 2 || !pieces.get(1).id().equals(PRAIRIE_PIECE + kind + "_0_top")) {
            throw new IllegalArgumentException("Prairie house needs its matching upper piece");
        }
        checkFrame(root, root.origin(), root.rotation(), 12, 1, 16);
        checkFrame(pieces.get(1), root.origin().above(), root.rotation(), 12, 17, 16);
        return new GroundHouseLayout(kind.equals("clean") ? Variant.CLEAN : Variant.RUINS, root.origin(), root.rotation());
    }

    private static GroundHouseLayout mushroom(WdaTemplatePiece root, List<WdaTemplatePiece> pieces) {
        checkFrame(root, root.origin(), root.rotation(), 15, 8, 15);
        var offset = new BlockPos(-8,0,-8).rotate(root.rotation()).offset(root.origin());
        var sockets = new HashMap<BlockPos, Direction>();
        addSockets(root, FLOORS.get(root.id()), sockets);
        boolean hasCap = false, hasRoots = false;
        var spawners = new ArrayList<WdaTemplatePiece>();
        for (var piece : pieces.subList(1, pieces.size())) {
            if (CAPS.containsKey(piece.id()) && !hasCap) {
                checkFrame(piece, offset.above(8), root.rotation(), 31, 31, 31);
                addSockets(piece, CAPS.get(piece.id()), sockets);
                hasCap = true;
            } else if (piece.id().equals(ROOTS) && !hasRoots) {
                checkFrame(piece, offset.below(13), root.rotation(), 31, 13, 31);
                hasRoots = true;
            } else if (SPAWNERS.contains(piece.id())) {
                spawners.add(piece);
            } else {
                throw new IllegalArgumentException("Unexpected or duplicate mushroom house piece " + piece.id());
            }
        }
        if (!hasRoots || !hasCap) throw new IllegalArgumentException("Missing original mushroom roots or cap");
        for (var piece : spawners) {
            // The down-facing inlet sits one block above the spawner. A socket can have at most one child.
            if (sockets.remove(piece.origin()) != piece.rotation().rotate(Direction.NORTH)) {
                throw new IllegalArgumentException("Mushroom spawner does not fit an available native socket");
            }
            checkFrame(piece, piece.origin(), piece.rotation(), 1, 2, 1);
        }
        return new GroundHouseLayout(Variant.MUSHROOM, root.origin(), root.rotation());
    }

    private static void addSockets(WdaTemplatePiece piece, List<Socket> sockets, Map<BlockPos, Direction> available) {
        for (var socket : sockets) {
            var position = new BlockPos(socket.x(), socket.y(), socket.z()).rotate(piece.rotation()).offset(piece.origin());
            available.put(position, piece.rotation().rotate(socket.top()));
        }
    }

    private static void checkFrame(WdaTemplatePiece piece, BlockPos origin, Rotation rotation, int width, int height, int depth) {
        var corner = new BlockPos(width - 1, height - 1, depth - 1).rotate(rotation).offset(origin);
        var bounds = new BlockBounds(Math.min(origin.getX(), corner.getX()), origin.getY(), Math.min(origin.getZ(), corner.getZ()),
                Math.max(origin.getX(), corner.getX()), corner.getY(), Math.max(origin.getZ(), corner.getZ()));
        if (!piece.origin().equals(origin) || piece.rotation() != rotation || !bounds.equals(piece.bounds())) {
            throw new IllegalArgumentException("Misaligned or resized original piece " + piece.id());
        }
    }

    Cell at(int x, int y, int z) {
        var point = new BlockPos(x,y,z).rotate(rotation).offset(origin);
        return new Cell(point.getX(), point.getY(), point.getZ());
    }

    @Override public GroundHouseLayout move(int x, int y, int z) {
        return new GroundHouseLayout(variant, origin.offset(x,y,z), rotation);
    }

    @Override public BooleanSupplier survey(ServerLevel world, IslandPlan island, int exits) {
        return GroundHouseRoute.survey(this, island, exits, new GroundHouseProbe(world));
    }
}
