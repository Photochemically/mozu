package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.InteriorSiteSearch;
import dev.expedition.bridge.domain.IslandPlan;
import java.util.*;

/** All exits and required approaches must share dry ground with at least one alternative approach. */
final class DryPerimeterRoute {
    private final IslandPlan island;
    private final InteriorSiteSearch.Probe world;
    private final List<Cell> exits;
    private final Set<Cell> remaining;
    private final Set<Cell> alternatives;
    private final Set<Cell> seen = new HashSet<>();
    private final ArrayDeque<Cell> queue = new ArrayDeque<>();
    private boolean reachedAlternative;
    private int surveyed;

    DryPerimeterRoute(IslandPlan island, int count, InteriorSiteSearch.Probe world,
                      Collection<Cell> required, Collection<Cell> alternatives) {
        this.island = island;
        this.world = world;
        exits = island.perimeterSites(count);
        remaining = new HashSet<>(required);
        remaining.addAll(exits);
        this.alternatives = Set.copyOf(alternatives);
        if (this.alternatives.isEmpty()) throw new IllegalStateException("No usable approach to the original structure");
    }

    boolean step() {
        if (surveyed < exits.size()) {
            if (!InteriorSiteSearch.clearPlatform(exits.get(surveyed++), world))
                throw new IllegalStateException("Obstructed perimeter exit");
            if (surveyed == exits.size()) visit(exits.get(0));
            return false;
        }
        var p = queue.pollFirst();
        if (p == null) throw new IllegalStateException("No connected dry route to every requested exit and entrance");
        remaining.remove(p);
        if (alternatives.contains(p)) reachedAlternative = true;
        if (remaining.isEmpty() && reachedAlternative) return true;
        visit(p.move(1,0,0));
        visit(p.move(-1,0,0));
        visit(p.move(0,0,1));
        visit(p.move(0,0,-1));
        return false;
    }

    private void visit(Cell p) {
        if (p.x() < island.coreMinX() || p.x() > island.coreMaxX() || p.z() < island.coreMinZ() || p.z() > island.coreMaxZ()) return;
        if (seen.add(p) && world.at(p.move(0,-1,0)) == Type.SUPPORT && world.at(p) == Type.AIR && world.at(p.move(0,1,0)) == Type.AIR)
            queue.addLast(p);
    }
}
