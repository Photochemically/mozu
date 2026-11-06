package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.InteriorSiteSearch;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.List;

/** Survey the entire approach before placing the external scaffold. Each step runs inside the build budget. */
final class BathhouseRoute {
    interface Probe extends InteriorSiteSearch.Probe {
        boolean door(Cell feet, String wood, Direction facing);
        boolean closedTopHatch(Cell support);
        boolean source(BlockPos p);
        boolean scaffold(Cell p);
        void addScaffold(Cell p);
    }

    private final BathhouseLayout bath;
    private final Probe world;
    private final Cell topFeet;
    private final DryPerimeterRoute ground;
    private boolean groundReady;
    private int waters, scanned, placed, checked;

    BathhouseRoute(BathhouseLayout bath, IslandPlan island, int exits, Probe world) {
        this.bath = bath;
        this.world = world;
        topFeet = bath.at(bath.east() ? 31 : -1, bath.east() ? 9 : 2, 15);
        var outside = bath.at(bath.east() ? 32 : -2, 0, 15);
        var approach = new Cell(outside.x(), bath.groundY(), outside.z());
        // The native beard_box extends 12 blocks past the wall. The porch coordinates come from the actual middle piece.
        if (island.firstFreeY() != bath.groundY() || outside.x() < island.coreMinX() || outside.x() > island.coreMaxX()
                || outside.z() < island.coreMinZ() || outside.z() > island.coreMaxZ()) {
            throw new IllegalStateException("Bathhouse approach must meet the island core");
        }
        ground = new DryPerimeterRoute(island, exits, world, List.of(), List.of(approach),
                p -> (p.x() != topFeet.x() || p.z() != topFeet.z()) && world.at(p.move(0,-1,0)) == Type.SUPPORT
                        && world.at(p) == Type.AIR && world.at(p.move(0,1,0)) == Type.AIR);
    }
    private Cell column(int index) { return new Cell(topFeet.x(), bath.groundY()+index, topFeet.z()); }

    boolean step() {
        if (!groundReady) {
            if (!ground.step()) return false;
            checkPorch();
            if (world.at(column(-1)) != Type.SUPPORT) throw new IllegalStateException("Unsupported bathhouse scaffold");
            groundReady = true;
            return false;
        }
        if (waters < bath.waterSources().size()) {
            if (!world.source(bath.waterSources().get(waters++))) throw new IllegalStateException("Original bathhouse water source missing");
            return false;
        }
        int height = topFeet.y() - bath.groundY();
        if (scanned < height) {
            var cell = column(scanned++);
            if (world.at(cell) != Type.AIR && !world.scaffold(cell)) throw new IllegalStateException("Occupied scaffold column at " + cell);
            return false;
        }
        if (placed < height) {
            world.addScaffold(column(placed++));
            return false;
        }
        if (checked < height) {
            if (!world.scaffold(column(checked++))) throw new IllegalStateException("Incomplete bathhouse scaffold");
            return false;
        }
        checkPorch();
        return true;
    }

    private void checkPorch() {
        air(topFeet);
        air(topFeet.move(0,1,0));
        if (bath.east()) {
            // The porch drops one block onto carpet over a closed top trapdoor. Skirt the spawner at (27,8,15).
            floor(30,9,15);
            var hatch = bath.at(29,7,15);
            if (!world.closedTopHatch(hatch)) throw new IllegalStateException("Unsupported eastern porch");
            clearance(bath.at(29,8,15));
            air(bath.at(29,10,15));
            floor(28,8,15);
            for (int x=28; x>=26; x--) floor(x,8,14);
            floor(26,8,15);
            door(25,8,"birch",Direction.WEST);
            for (int x=24; x>=22; x--) floor(x,8,15);
        } else {
            floor(0,2,15);
            floor(1,1,15);
            air(bath.at(1,3,15));
            floor(2,1,15);
            door(3,1,bath.middleId().equals("middle_2") ? "dark_oak" : "spruce",Direction.EAST);
            for (int x=4; x<=6; x++) floor(x,1,15);
        }
    }
    private void door(int x,int y,String wood,Direction direction) {
        var p = bath.at(x,y,15);
        if (world.at(p.move(0,-1,0)) != Type.SUPPORT || !world.door(p,wood,bath.rotation().rotate(direction))) {
            throw new IllegalStateException("Changed bathhouse doorway at " + p);
        }
    }
    private void floor(int x,int y,int z) {
        var p = bath.at(x,y,z);
        if (world.at(p.move(0,-1,0)) != Type.SUPPORT) throw new IllegalStateException("Missing dry porch floor at " + p);
        clearance(p);
    }
    private void clearance(Cell feet) {
        var type = world.at(feet);
        if (type != Type.AIR && type != Type.FLOOR_DECOR) throw new IllegalStateException("Obstructed bathhouse floor at " + feet);
        air(feet.move(0,1,0));
    }
    private void air(Cell p) {
        if (world.at(p) != Type.AIR) throw new IllegalStateException("Obstructed bathhouse entry at " + p);
    }
}
