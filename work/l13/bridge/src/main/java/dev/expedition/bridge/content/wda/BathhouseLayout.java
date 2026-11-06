package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.DungeonSpec;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Keep the entire original base above the planned island; the porch belongs to the middle piece. */
record BathhouseLayout(String middleId, BlockPos middleOrigin, Rotation rotation, int groundY,
                       List<BlockPos> waterSources) implements PerimeterLayout {
    static final String TARGET = "dungeons_arise:bathhouse";
    static final String PREFIX = TARGET + "/bathhouse_";
    private static final Map<String,Integer> BASE = Map.of("base_0",12,"base_1",11,"base_2",11,"base_3",13,"base_4",6,"base_5",21);
    private static final Map<String,Integer> MIDDLE = Map.of("middle_0",17,"middle_1",17,"middle_2",17,"middle_3",22);
    private static final Map<String,Integer> TOP = Map.of("top_0",13,"top_1",19,"top_2",27,"top_3",20,"top_4",13,"top_5",21,"top_6",21);
    private static final Set<String> SPAWNER = Set.of("spawner_0","spawner_1","spawner_2");

    private static String name(String id) {
        if (!id.startsWith(PREFIX)) throw new IllegalArgumentException("Foreign bathhouse piece " + id);
        return id.substring(PREFIX.length());
    }
    static String processors(String id) {
        String part = name(id);
        if (MIDDLE.containsKey(part) || TOP.containsKey(part)) return "dungeons_arise:bathhouse_main";
        if (BASE.containsKey(part) || SPAWNER.contains(part)) return "minecraft:empty";
        throw new IllegalArgumentException("Unknown bathhouse piece " + id);
    }

    static BathhouseLayout inspect(List<WdaTemplatePiece> pieces, WdaStructurePlanner.Environment env,
                                   Set<BlockPos> sockets, List<BlockPos> waterSources) {
        if (pieces.isEmpty() || env.boarding() || env.entryMode() != DungeonSpec.EntryMode.PERIMETER
                || (env.surface() != IslandPlan.Surface.GRASS && env.surface() != IslandPlan.Surface.ROCK)) {
            throw new IllegalArgumentException("Bathhouse requires a dry island perimeter");
        }
        var root = pieces.get(0);
        var height = BASE.get(name(root.id()));
        if (height == null || root.origin().getY() != env.firstFreeY()-1 || pieces.stream().anyMatch(WdaTemplatePiece::terrainMatching)) {
            throw new IllegalArgumentException("Invalid bathhouse base or ground height");
        }
        root.requireFrame(root.origin(), root.rotation(), 31, height, 31);
        WdaTemplatePiece middle = null, roof = null;
        var occupied = new HashSet<BlockPos>();
        for (var piece : pieces.subList(1,pieces.size())) {
            String part = name(piece.id());
            if (MIDDLE.containsKey(part) && middle == null) middle = piece;
            else if (TOP.containsKey(part) && roof == null) roof = piece;
            else if (SPAWNER.contains(part) && sockets.contains(piece.origin()) && occupied.add(piece.origin())) {
                piece.requireFrame(piece.origin(), piece.rotation(), 1, 2, 1);
            } else throw new IllegalArgumentException("Unexpected or detached bathhouse piece " + piece.id());
        }
        if (middle == null || roof == null) throw new IllegalArgumentException("Incomplete three-storey bathhouse");
        String middleName = name(middle.id());
        int middleHeight = MIDDLE.get(middleName);
        middle.requireFrame(root.origin().above(height), root.rotation(), 31, middleHeight, 31);
        roof.requireFrame(middle.origin().above(middleHeight), root.rotation(), 31, TOP.get(name(roof.id())), 31);
        // The inbound air jigsaw would erase the loot chest located immediately above this socket.
        if (name(roof.id()).equals("top_3") && occupied.contains(new BlockPos(20,6,15).rotate(roof.rotation()).offset(roof.origin()))) {
            throw new IllegalArgumentException("Bathhouse spawner would replace original roof chest");
        }
        return new BathhouseLayout(middleName, middle.origin(), root.rotation(), env.firstFreeY(), List.copyOf(waterSources));
    }
    boolean east() { return middleId.equals("middle_3"); }
    Cell at(int x,int y,int z) {
        var p = new BlockPos(x,y,z).rotate(rotation).offset(middleOrigin);
        return new Cell(p.getX(),p.getY(),p.getZ());
    }
    @Override public BathhouseLayout move(int x,int y,int z) {
        return new BathhouseLayout(middleId, middleOrigin.offset(x,y,z), rotation, groundY+y,
                waterSources.stream().map(p -> p.offset(x,y,z)).toList());
    }
    @Override public BooleanSupplier survey(ServerLevel world, IslandPlan island, int exits) {
        return new BathhouseRoute(this,island,exits,new BathhouseProbe(world))::step;
    }
}
