package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.DungeonSpec;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import java.util.List;
import java.util.function.BooleanSupplier;

record FishingHutLayout(BlockPos origin, Rotation rotation, boolean boarding, boolean hasSpawner) implements PerimeterLayout {
    static final String TARGET = "dungeons_arise:fishing_hut";
    static final String MAIN = TARGET + "/fishing_hut_main";
    static final String SPAWNER = TARGET + "/fishing_hut_spawner_0";

    static FishingHutLayout inspect(List<WdaTemplatePiece> pieces, WdaStructurePlanner.Environment env) {
        if (env.entryMode() != DungeonSpec.EntryMode.PERIMETER || env.surface() == IslandPlan.Surface.VOID
                || env.waterDepth() > 3) {
            throw new IllegalArgumentException("Fishing hut needs dry ground or water shallow enough to support the original piles");
        }
        if (pieces.isEmpty() || pieces.size() > 2 || pieces.stream().anyMatch(WdaTemplatePiece::terrainMatching)) {
            throw new IllegalArgumentException("Expected the rigid fishing hut and at most one original spawner");
        }
        var root = pieces.get(0);
        // WORLD_SURFACE_WG plus start_height -2, minus the single element's ground level delta of one.
        if (!MAIN.equals(root.id()) || root.origin().getY() != env.firstFreeY() - 3) {
            throw new IllegalArgumentException("Unexpected fishing hut template or waterline");
        }
        root.requireFrame(root.origin(),root.rotation(),21,14,17);
        if (pieces.size() == 2) {
            var child = pieces.get(1);
            if (!SPAWNER.equals(child.id())) throw new IllegalArgumentException("Unexpected fishing hut encounter");
            var socket = new BlockPos(10,5,5).rotate(root.rotation()).offset(root.origin());
            child.requireFrame(socket,root.rotation(),1,2,1);
        }
        return new FishingHutLayout(root.origin(),root.rotation(),env.boarding(),pieces.size()==2);
    }

    Cell at(int x,int y,int z) {
        var pos = new BlockPos(x,y,z).rotate(rotation).offset(origin);
        return new Cell(pos.getX(),pos.getY(),pos.getZ());
    }

    @Override public FishingHutLayout move(int x,int y,int z) {
        return new FishingHutLayout(origin.offset(x,y,z),rotation,boarding,hasSpawner);
    }
    @Override public BooleanSupplier survey(ServerLevel world, IslandPlan island, int exits) {
        return new FishingHutRoute(this,island,exits,new FishingHutProbe(world))::step;
    }
}
