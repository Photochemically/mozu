package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.InteriorSiteSearch;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.Direction;
import java.util.*;

final class BanditRoute {
    interface Probe extends InteriorSiteSearch.Probe {
        boolean stair(Cell p,String kind,Direction facing);
        boolean feature(BanditLayout.Feature f);
        boolean stepBlock(Cell p);
        void addStep(Cell p);
    }
    private final BanditLayout village;
    private final Probe world;
    private final DryPerimeterRoute ground;
    private final List<Cell> steps;
    private int checked,placed;
    private boolean surveyed;
    BanditRoute(BanditLayout village,IslandPlan island,int exits,Probe world) {
        if(island.firstFreeY()!=village.groundY())throw new IllegalStateException("Bandit ground is off grade");
        this.village=village;this.world=world;
        steps=village.plots().stream().map(BanditLayout.Plot::stepBlock).filter(Objects::nonNull).toList();
        ground=new DryPerimeterRoute(island,exits,world,village.plots().stream().map(BanditLayout.Plot::approach).toList(),
                List.of(village.center()),p->!steps.contains(p)&&world.at(p.move(0,-1,0))==Type.SUPPORT
                        &&world.at(p)==Type.AIR&&world.at(p.move(0,1,0))==Type.AIR);
    }
    boolean step() {
        if(!surveyed) {
            if(checked<village.plots().size()){checkPlot(village.plots().get(checked++));return false;}
            if(!ground.step())return false;
            checkFeatures();
            for(var p:steps)checkStep(p);
            surveyed=true;
        }
        if(placed<steps.size()) {
            var p=steps.get(placed++);checkStep(p);
            if(!world.stepBlock(p))world.addStep(p);
            return false;
        }
        for(var p:steps)if(!world.stepBlock(p))throw new IllegalStateException("Incomplete bandit entrance step");
        for(var p:village.plots())checkPlot(p);
        checkFeatures();return true;
    }
    private void checkStep(Cell p) {
        if(world.at(p.move(0,-1,0))!=Type.SUPPORT || (world.at(p)!=Type.AIR&&!world.stepBlock(p)))
            throw new IllegalStateException("Bandit step would replace an original block or has no footing");
        for(int y=1;y<=3;y++)if(world.at(p.move(0,y,0))!=Type.AIR)throw new IllegalStateException("Blocked bandit step");
    }
    private void checkFeatures() {
        for(var f:village.features())if(!world.feature(f))throw new IllegalStateException("Changed native bandit feature at "+f.pos());
    }
    // Original first-room routes. The two added steps are outside template footprints.
    static int[][] path(String id) {
        return switch(id) {
            case "house_0","house_7" -> new int[][]{{3,1,-1},{3,2,0},{3,2,1},{3,2,2},{3,2,3},{3,2,4}};
            case "house_1" -> new int[][]{{-2,0,3},{-1,1,3},{0,2,3},{1,2,3},{2,2,3}};
            case "house_2" -> new int[][]{{3,1,-1},{3,2,0},{3,2,1},{3,1,2}};
            case "house_3","house_8" -> new int[][]{{-1,1,3},{0,2,3},{1,2,3},{2,2,3},{3,2,3}};
            case "house_4" -> new int[][]{{5,0,-2},{5,1,-1},{5,2,0},{4,3,0},{3,4,0},{2,4,0},{2,4,1},{2,4,2}};
            case "house_5" -> new int[][]{{3,1,-1},{3,2,0},{3,2,1},{3,2,2},{3,2,3}};
            case "house_6" -> new int[][]{{7,1,4},{6,2,4},{5,2,4},{4,2,4},{3,2,4}};
            case "house_9" -> new int[][]{{3,1,-1},{3,2,0},{3,2,1},{3,2,2}};
            default -> new int[0][];
        };
    }
    private void checkPlot(BanditLayout.Plot plot) {
        var route=path(plot.id());
        for(int i=0;i<route.length;i++) {
            var p=route[i];var feet=plot.at(p[0],p[1],p[2]);var floor=feet.move(0,-1,0);
            String stair=null;Direction facing=null;
            if(plot.id().equals("house_1")&&i==2){stair="brick_stairs";facing=Direction.EAST;}
            if(plot.id().equals("house_2")&&i==2){stair="jungle_stairs";facing=Direction.NORTH;}
            if(plot.id().equals("house_4")&&i>=2&&i<=4){stair="jungle_stairs";facing=Direction.WEST;}
            if(floor.equals(plot.stepBlock()))checkStep(floor);
            else if(stair!=null?!world.stair(floor,stair,plot.rotation().rotate(facing)):world.at(floor)!=Type.SUPPORT)
                throw new IllegalStateException("Missing native bandit entrance footing "+plot.id()+" "+floor);
            int high=p[1];if(i>0)high=Math.max(high,route[i-1][1]);if(i+1<route.length)high=Math.max(high,route[i+1][1]);
            for(int y=p[1];y<high+1.8;y++)if(world.at(plot.at(p[0],y,p[2]))!=Type.AIR)
                throw new IllegalStateException("Blocked native bandit entrance "+plot.id()+" "+plot.at(p[0],y,p[2]));
        }
    }
}
