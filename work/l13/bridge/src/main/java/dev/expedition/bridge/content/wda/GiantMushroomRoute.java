package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.InteriorSiteSearch;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.function.BooleanSupplier;

final class GiantMushroomRoute {
    interface Probe extends InteriorSiteSearch.Probe {
        boolean stem(Cell support);
        boolean lootChest(BlockPos pos,String table);
        boolean skeletonSpawner(BlockPos pos);
    }

    // Feet positions along continuous faces of the original stem. The first is on the island surface.
    private static final List<BlockPos> RED = List.of(new BlockPos(30,6,32),new BlockPos(31,7,32),new BlockPos(32,7,32),
            new BlockPos(33,7,32),new BlockPos(33,8,31),new BlockPos(32,9,31),new BlockPos(31,10,31),new BlockPos(30,11,31));
    private static final List<BlockPos> TWIN = List.of(new BlockPos(15,6,31),new BlockPos(15,7,32),new BlockPos(15,7,33),
            new BlockPos(15,7,34),new BlockPos(16,8,34),new BlockPos(17,9,34),new BlockPos(18,9,34),new BlockPos(18,9,35),
            new BlockPos(19,10,35),new BlockPos(20,10,35),new BlockPos(20,11,34),new BlockPos(21,12,34));

    static BooleanSupplier survey(GiantMushroomLayout layout,IslandPlan island,int exits,Probe world) {
        var path=layout.form()==GiantMushroomLayout.Form.RED ? RED : TWIN;
        var approach=layout.cell(path.get(0));
        if (approach.y()!=island.firstFreeY()) throw new IllegalStateException("Giant mushroom approach is off grade");
        var ground=new DryPerimeterRoute(island,exits,world,List.of(),List.of(approach));
        return () -> {
            if (!ground.step()) return false;
            for (int i=0;i<path.size();i++) {
                var feet=layout.cell(path.get(i));
                var support=feet.move(0,-1,0);
                if (world.at(support)!=Type.SUPPORT || !world.stem(support))
                    throw new IllegalStateException("Missing original giant mushroom foothold at "+support);
                boolean jumping=i+1<path.size() && path.get(i+1).getY()>path.get(i).getY();
                for (int h=0;h<=(jumping ? 3 : 2);h++) if (world.at(feet.move(0,h,0))!=Type.AIR)
                    throw new IllegalStateException("Blocked giant mushroom route at "+feet);
            }
            for (var chest:layout.form().chests) if (!world.lootChest(layout.position(chest),layout.form().loot()))
                throw new IllegalStateException("Missing or blocked original giant mushroom loot chest");
            for (var socket:layout.form().spawners) if (!world.skeletonSpawner(layout.position(socket)))
                throw new IllegalStateException("Missing original giant mushroom skeleton spawner");
            return true;
        };
    }
}
