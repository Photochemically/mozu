package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.InteriorSiteSearch;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.Direction;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** Read-only flood fill over the finished field layout. Each step inspects one exit, staircase or ground cell. */
final class WindmillRoute {
    interface Probe extends InteriorSiteSearch.Probe {
        boolean originalStep(Cell p, Direction facing, boolean andesite);
    }

    private final WindmillLayout mill;
    private final IslandPlan island;
    private final Probe world;
    private final int exits;
    private final Set<Cell> approaches = new HashSet<>();
    private DryPerimeterRoute ground;
    private int surveyed;

    WindmillRoute(WindmillLayout mill, IslandPlan island, int count, Probe world) {
        this.mill = mill;
        this.island = island;
        this.world = world;
        this.exits = count;
    }

    boolean step() {
        if (surveyed < 2) {
            int dx = surveyed++ == 0 ? 1 : -1;
            if (openStairs(dx)) approaches.add(mill.at(dx == 1 ? 4 : 44, 3, 18));
            return false;
        }
        if (ground == null) ground = new DryPerimeterRoute(island, exits, world, List.of(), approaches);
        return ground.step();
    }

    private boolean openStairs(int dx) {
        int x = dx == 1 ? 5 : 43;
        Direction facing = mill.rotation().rotate(dx == 1 ? Direction.EAST : Direction.WEST);
        for (int i = 0; i < 7; i++) {
            var p = mill.at(x + dx * i, 3 + i, 18);
            boolean andesite = i == 1 || i == 2 || i == 4;
            if (!world.originalStep(p, facing, andesite) || !clear(p.move(0, 1, 0)) || world.at(p.move(0, 3, 0)) != Type.AIR) return false;
        }
        int landing = x + 7 * dx;
        if (!walkable(mill.at(landing, 10, 18))) return false;
        for (int i = 0; i < 6; i++) if (!walkable(mill.at(landing + dx * i, 10, 17))) return false;
        return true;
    }

    private boolean walkable(Cell p) { return world.at(p.move(0, -1, 0)) == Type.SUPPORT && clear(p); }
    private boolean clear(Cell p) { return world.at(p) == Type.AIR && world.at(p.move(0, 1, 0)) == Type.AIR; }
}
