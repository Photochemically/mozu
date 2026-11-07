package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.InteriorSiteSearch;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.List;
import java.util.function.BooleanSupplier;

final class TreeHouseRoute {
    interface Probe extends InteriorSiteSearch.Probe {
        boolean originalSupport(Cell p);
        boolean loot(BlockPos pos,String kind,String table);
        boolean ladder(BlockPos pos,Direction facing);
    }
    // The native spiral reaches the inner wooden platform at Y=24. Coordinates are feet positions.
    private static final List<BlockPos> SPIRAL=List.of(
            new BlockPos(23,6,14),new BlockPos(24,7,14),new BlockPos(24,8,13),new BlockPos(24,9,12),
            new BlockPos(24,9,11),new BlockPos(24,10,10),new BlockPos(23,10,10),new BlockPos(22,10,10),
            new BlockPos(22,11,9),new BlockPos(21,12,9),new BlockPos(21,12,8),new BlockPos(20,13,8),
            new BlockPos(19,13,8),new BlockPos(18,14,8),new BlockPos(17,15,8),new BlockPos(16,16,8),
            new BlockPos(15,16,8),new BlockPos(15,15,7),new BlockPos(14,15,7),new BlockPos(13,15,7),
            new BlockPos(12,16,7),new BlockPos(11,16,7),new BlockPos(10,16,7),new BlockPos(10,16,8),
            new BlockPos(10,17,9),new BlockPos(10,17,10),new BlockPos(10,18,11),new BlockPos(10,19,12),
            new BlockPos(10,20,13),new BlockPos(11,20,13),new BlockPos(11,21,14),new BlockPos(12,21,14),
            new BlockPos(13,21,14),new BlockPos(13,22,13),new BlockPos(14,22,13),new BlockPos(15,22,13),
            new BlockPos(15,23,12),new BlockPos(16,23,12),new BlockPos(17,24,12),new BlockPos(18,24,12),
            new BlockPos(18,24,11),new BlockPos(18,24,10),new BlockPos(18,24,9));

    static BooleanSupplier survey(TreeHouseLayout tree,IslandPlan island,int exits,Probe world) {
        if (island.firstFreeY()!=tree.groundY()) throw new IllegalStateException("Tree house ground is off grade");
        var ground=new DryPerimeterRoute(island,exits,world,List.of(),List.of(tree.cell(SPIRAL.get(0))));
        return () -> {
            if (!ground.step()) return false;
            for (int i=0;i<SPIRAL.size();i++) {
                var local=SPIRAL.get(i); var feet=tree.cell(local);
                if (i==0 ? world.at(feet.move(0,-1,0))!=Type.SUPPORT : !world.originalSupport(feet.move(0,-1,0))) throw new IllegalStateException("Missing native tree house footing");
                boolean risingAhead=i+1<SPIRAL.size() && SPIRAL.get(i+1).getY()>local.getY();
                boolean risingBack=i>0 && SPIRAL.get(i-1).getY()>local.getY();
                for (int dy=0;dy<=(risingAhead || risingBack?3:2);dy++) if (world.at(feet.move(0,dy,0))!=Type.AIR)
                    throw new IllegalStateException("Obstructed tree house spiral");
            }
            for (var loot:TreeHouseLayout.LOOT) if (!world.loot(tree.pos(loot.position()),loot.kind(),loot.table()))
                throw new IllegalStateException("Changed original tree house loot");
            for (int y=24;y<=31;y++) if (!world.ladder(tree.pos(new BlockPos(15,y,15)),tree.rotation().rotate(Direction.EAST)))
                throw new IllegalStateException("Broken original upper tree house ladder");
            return true;
        };
    }
}
