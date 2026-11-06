package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.InteriorSiteSearch;
import dev.expedition.bridge.domain.IslandPlan;
import java.util.*;

/** Follow either of the original two landing lanes, then the open doorway and the barrel counter. */
final class FishingHutRoute {
    interface Probe extends InteriorSiteSearch.Probe {
        boolean oakPlanks(Cell p);
        boolean sprucePile(Cell p);
        boolean barrel(Cell p);
        boolean originalEncounter(Cell socket,boolean hasSpawner);
        boolean waterSource(Cell p);
        void bridgeSource(Cell p);
    }
    private final FishingHutLayout hut;
    private final IslandPlan island;
    private final Probe world;
    private final int exits;
    private final Map<Cell,Integer> lanes = new LinkedHashMap<>();
    private DryPerimeterRoute ground;

    FishingHutRoute(FishingHutLayout hut, IslandPlan island, int exits, Probe world) {
        this.hut = hut;
        this.island = island;
        this.world = world;
        this.exits = exits;
        if (island.firstFreeY() != hut.origin().getY()+3) throw new IllegalStateException("Fishing hut deck is off grade");
    }

    boolean step() {
        if (ground == null) {
            inspectRoom();
            for (int lane : List.of(10,11)) {
                if (usableLane(lane)) lanes.put(hut.at(-2,3,lane),lane);
            }
            // The bank walk stays outside the hut and basin even if a dock lane already has a plank.
            ground = new DryPerimeterRoute(island,exits,world,List.of(),lanes.keySet(),
                    p -> !island.building().contains(p.x(),p.y(),p.z()) && !island.inBasin(p.x(),p.z())
                            && world.at(p.move(0,-1,0)) == Type.SUPPORT && clear(p));
        }
        if (!ground.step()) return false;
        int lane = lanes.get(ground.reachedApproach());
        inspectRoom();
        if (!usableLane(lane)) throw new IllegalStateException("Fishing hut landing changed during survey");
        var gap = hut.at(-1,2,lane);
        if (hut.boarding() && !world.oakPlanks(gap)) {
            world.bridgeSource(gap);
            if (!world.oakPlanks(gap)) throw new IllegalStateException("Fishing hut gangway placement failed");
        }
        return true;
    }

    private void inspectRoom() {
        // The six native spruce posts sit in or directly on solid ground at depths two and three.
        for (int z : List.of(7,14)) for (int x : List.of(4,16)) checkPile(x,z);
        checkPile(7,2);
        checkPile(13,2);
        for (int x=2; x<=10; x++) requireDeck(x,10);
        for (int z=9; z>=6; z--) requireDeck(10,z);
        requireDeck(8,9);
        requireDeck(9,9);
        if (!world.originalEncounter(hut.at(10,5,5),hut.hasSpawner())) throw new IllegalStateException("Changed fishing hut encounter");
        if (!world.barrel(hut.at(8,3,8)) || !world.barrel(hut.at(8,4,8)) || !world.barrel(hut.at(9,3,8))) {
            throw new IllegalStateException("Missing original fishing hut barrels");
        }
    }
    private void checkPile(int x,int z) {
        var foot = hut.at(x,0,z);
        if (!world.sprucePile(foot) || world.at(foot.move(0,-1,0)) != Type.SUPPORT) {
            throw new IllegalStateException("Unsupported original fishing hut pile at " + foot);
        }
    }
    private boolean usableLane(int z) {
        if (!deck(0,z) || !deck(1,z) || !deck(2,z)) return false;
        var bank = hut.at(-2,3,z);
        var gap = hut.at(-1,2,z);
        if (world.at(bank.move(0,-1,0)) != Type.SUPPORT || !clear(bank) || !clear(gap.move(0,1,0))) return false;
        if (island.waterDepth() == 0) return world.at(gap) == Type.SUPPORT;
        return island.inBasin(gap.x(),gap.z()) && world.waterSource(gap.move(0,-1,0))
                && (world.waterSource(gap) || hut.boarding() && world.oakPlanks(gap));
    }
    private boolean clear(Cell feet) { return world.at(feet) == Type.AIR && world.at(feet.move(0,1,0)) == Type.AIR; }
    private boolean deck(int x,int z) { return world.oakPlanks(hut.at(x,2,z)) && clear(hut.at(x,3,z)); }
    private void requireDeck(int x,int z) {
        if (!deck(x,z)) throw new IllegalStateException("Obstructed original fishing hut floor at " + hut.at(x,3,z));
    }
}
