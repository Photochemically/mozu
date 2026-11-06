package dev.expedition.bridge.domain;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import java.util.*;

/** Read-only survey from the requested island exits to the foot of the original tower ladder. */
public final class LighthouseRoute {
    public interface Probe extends InteriorSiteSearch.Probe {
        boolean originalStep(Cell block, int turns);
        boolean originalLadder(Cell block, int turns);
    }
    private enum Check { WALK, STEP, LADDER, PLATFORM }
    private record Station(Cell position, Check check) {}

    private final LighthouseLayout layout;
    private final Probe world;
    private final List<Cell> ring = new ArrayList<>();
    private final Set<Cell> openRing = new HashSet<>();
    private final List<Station> stations = new ArrayList<>();
    private final List<Cell> exits;
    private int cursor;

    public LighthouseRoute(LighthouseLayout layout, IslandPlan island, int exitCount, Probe world) {
        this.layout = layout;
        this.world = world;
        if (island.firstFreeY() != layout.groundY()) throw new IllegalArgumentException("Buried or floating first step");
        exits = island.perimeterSites(exitCount);
        // The existing island doors are on this square. An obstacle on the unused side can be walked around.
        for (int i = -3; i < 33; i++) {
            ring.add(layout.at(i, 0, -3));
            ring.add(layout.at(33, 0, i));
            ring.add(layout.at(30 - i, 0, 33));
            ring.add(layout.at(-3, 0, 30 - i));
        }
        for (var exit : exits) stations.add(new Station(exit, Check.PLATFORM));
        for (int z = -3; z < 6; z++) stations.add(new Station(layout.at(15, 0, z), Check.WALK));
        for (int step = 0; step < 4; step++) stations.add(new Station(layout.at(15, step, 6 + step), Check.STEP));
        for (int z = 10; z < 19; z++) stations.add(new Station(layout.at(15, 4, z), Check.WALK));
        stations.add(new Station(layout.at(15, 4, 19), Check.LADDER));
    }

    /** One bounded ring-cell scan, station check, or traversal of the known 144-cell ring. */
    public boolean step() {
        if (cursor < ring.size()) {
            var cell = ring.get(cursor++);
            if (walkable(cell)) openRing.add(cell);
        } else if (cursor == ring.size()) {
            checkConnectedExits();
            cursor++;
        } else {
            int index = cursor - ring.size() - 1;
            if (index >= stations.size()) return true;
            var station = stations.get(index);
            var p = station.position();
            boolean valid = switch (station.check()) {
                case PLATFORM -> InteriorSiteSearch.clearPlatform(p, world);
                case WALK -> walkable(p);
                case STEP -> world.originalStep(p, layout.turns()) && air(p.move(0, 1, 0))
                        && air(p.move(0, 2, 0)) && air(p.move(0, 3, 0));
                case LADDER -> world.at(p.move(0, -1, 0)) == Type.SUPPORT
                        && world.originalLadder(p, layout.turns()) && world.originalLadder(p.move(0, 1, 0), layout.turns());
            };
            if (!valid) throw new IllegalStateException("Lighthouse " + station.check() + " obstructed at " + p);
            cursor++;
        }
        return cursor == ring.size() + 1 + stations.size();
    }

    private void checkConnectedExits() {
        var approach = layout.at(15, 0, -3);
        var reachable = new HashSet<Cell>();
        var queue = new ArrayDeque<Cell>();
        if (openRing.contains(approach)) {
            queue.add(approach);
            reachable.add(approach);
        }
        while (!queue.isEmpty()) {
            var p = queue.removeFirst();
            for (var next : List.of(p.move(1, 0, 0), p.move(-1, 0, 0), p.move(0, 0, 1), p.move(0, 0, -1))) {
                if (openRing.contains(next) && reachable.add(next)) queue.addLast(next);
            }
        }
        if (!reachable.containsAll(exits)) throw new IllegalStateException("Lighthouse shore path is disconnected");
    }

    private boolean air(Cell cell) { return world.at(cell) == Type.AIR; }
    private boolean walkable(Cell p) {
        return world.at(p.move(0, -1, 0)) == Type.SUPPORT && air(p) && air(p.move(0, 1, 0)) && air(p.move(0, 2, 0));
    }
}
