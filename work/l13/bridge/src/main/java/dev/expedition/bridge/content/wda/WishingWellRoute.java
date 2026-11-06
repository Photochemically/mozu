package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.InteriorSiteSearch;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.*;

/** Only inspect the dry slopes up to the rim. No route node descends into the effect chamber. */
final class WishingWellRoute {
    interface Probe extends InteriorSiteSearch.Probe {
        boolean grass(Cell p);
        boolean rim(Cell p,Direction facing);
        boolean source(BlockPos p);
        boolean fixtures(WishingWellLayout well);
    }
    private record Lane(boolean east,int cross) {}
    // Local feet heights along the two original grass slopes, starting just outside the 16x16 template.
    private static final List<Integer> EAST = List.of(8,8,9,10,10,11);
    private static final List<Integer> SOUTH = List.of(8,8,8,9,10,11);

    private final WishingWellLayout well;
    private final IslandPlan island;
    private final int exits;
    private final Probe world;
    private final Map<Cell,Lane> lanes = new LinkedHashMap<>();
    private int waterIndex, laneIndex;
    private DryPerimeterRoute ground;

    WishingWellRoute(WishingWellLayout well,IslandPlan island,int exits,Probe world) {
        this.well=well; this.island=island; this.exits=exits; this.world=world;
        if (island.firstFreeY() != well.origin().getY()+8) throw new IllegalStateException("Wishing well ground is off grade");
    }

    boolean step() {
        if (waterIndex < well.sourceOffsets().size()) {
            var offset = well.sourceOffsets().get(waterIndex++);
            if (!world.source(offset.rotate(well.rotation()).offset(well.origin()))) throw new IllegalStateException("Original well water missing");
            return false;
        }
        if (laneIndex < 6) {
            var lane = new Lane(laneIndex < 3,6 + laneIndex%3);
            laneIndex++;
            if (usable(lane)) lanes.put(point(lane,16,8),lane);
            return false;
        }
        if (ground == null) {
            var box = well.bounds();
            ground = new DryPerimeterRoute(island,exits,world,List.of(),lanes.keySet(),p ->
                    !box.contains(p.x()+0.5,p.y(),p.z()+0.5) && world.at(p.move(0,-1,0))==Type.SUPPORT
                            && world.at(p)==Type.AIR && world.at(p.move(0,1,0))==Type.AIR);
        }
        if (!ground.step()) return false;
        if (!usable(lanes.get(ground.reachedApproach()))) throw new IllegalStateException("Wishing well slope changed");
        if (!world.fixtures(well)) throw new IllegalStateException("Original wishing well chamber changed");
        return true;
    }

    private Cell point(Lane lane,int distance,int y) {
        return lane.east() ? well.at(distance,y,lane.cross()) : well.at(lane.cross(),y,distance);
    }
    private boolean usable(Lane lane) {
        var heights = lane.east() ? EAST : SOUTH;
        for (int i=0;i<heights.size();i++) {
            var feet = point(lane,16-i,heights.get(i));
            if (world.at(feet.move(0,-1,0)) != Type.SUPPORT || (world.at(feet) != Type.AIR && !world.grass(feet))) return false;
            // Allow walking or a one-block jump in either direction, with headroom over the lower take-off cell.
            int clearance = i+1 < heights.size() && heights.get(i+1)>heights.get(i) ? 3 : 2;
            for (int y=1;y<=clearance;y++) if (world.at(feet.move(0,y,0)) != Type.AIR) return false;
        }
        var facing = well.rotation().rotate(lane.east() ? Direction.SOUTH : Direction.WEST);
        return world.rim(point(lane,11,10),facing);
    }
}
