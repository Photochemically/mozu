package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.InteriorSiteSearch;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.Direction;
import java.util.HashSet;
import java.util.Set;

final class MerchantCampRoute {
    interface Probe extends InteriorSiteSearch.Probe {
        boolean spruceDoor(Cell lower, Direction facing);
        boolean chest(Cell p);
    }
    private final MerchantCampLayout camp;
    private final IslandPlan island;
    private final Probe world;
    private final int exits;
    private final Set<Cell> approaches = new HashSet<>();
    private int surveyed;
    private DryPerimeterRoute ground;

    MerchantCampRoute(MerchantCampLayout camp, IslandPlan island, int exits, Probe world) {
        this.camp=camp; this.island=island; this.exits=exits; this.world=world;
    }

    boolean step() {
        if (surveyed < camp.tents().size()) {
            var tent = camp.tents().get(surveyed++);
            var aisle = tent.aisle();
            for (int z=aisle.first; z!=aisle.last+aisle.direction; z+=aisle.direction) {
                var p=tent.at(z);
                if (world.at(p.move(0,-1,0))!=Type.SUPPORT) throw new IllegalStateException("Unsupported tent aisle at " + p);
                if (aisle.door && z==1) {
                    if (!world.spruceDoor(p,tent.rotation().rotate(Direction.SOUTH))) throw new IllegalStateException("Broken original tent door at " + p);
                } else {
                    var feet=world.at(p);
                    if ((feet!=Type.AIR && feet!=Type.FLOOR_DECOR) || world.at(p.move(0,1,0))!=Type.AIR)
                        throw new IllegalStateException("Obstructed original tent aisle at " + p);
                }
            }
            approaches.add(tent.approach());
            return false;
        }
        if (ground==null) {
            if (!world.chest(camp.chest()) || world.at(camp.chest().move(0,1,0))!=Type.AIR)
                throw new IllegalStateException("Campsite map chest is missing or covered");
            ground = new DryPerimeterRoute(island,exits,world,approaches,camp.chestApproaches());
        }
        return ground.step();
    }
}
