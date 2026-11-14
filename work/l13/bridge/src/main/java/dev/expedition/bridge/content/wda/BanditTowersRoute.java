package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.InteriorSiteSearch;
import dev.expedition.bridge.domain.IslandPlan;
import java.util.*;

/** Climb beside a native broken bridge end without replacing its floor, loot or spawners. */
final class BanditTowersRoute {
    interface Probe extends InteriorSiteSearch.Probe {
        boolean feature(BanditTowersLayout.Feature f);
        boolean scaffold(Cell p);
        void addScaffold(Cell p);
    }
    private final BanditTowersLayout layout;
    private final Probe world;
    private final DryPerimeterRoute ground;
    private final Map<Cell,BanditTowersLayout.Landing> alternatives=new LinkedHashMap<>();
    private BanditTowersLayout.Landing chosen;
    private int nextY;
    BanditTowersRoute(BanditTowersLayout layout,IslandPlan island,int exits,Probe world){
        this.layout=layout;this.world=world;nextY=layout.groundY();
        if(island.firstFreeY()!=layout.groundY())throw new IllegalStateException("Tower island crosses original geometry");
        for(var l:layout.landings())if(usable(l)){
            var c=l.at(1,0,2);var front=l.rotation().rotate(net.minecraft.core.Direction.EAST);
            alternatives.put(new Cell(c.x()+front.getStepX(),layout.groundY(),c.z()+front.getStepZ()),l);
        }
        ground=new DryPerimeterRoute(island,exits,world,List.of(),alternatives.keySet(),p->!column(p)
                &&world.at(p.move(0,-1,0))==Type.SUPPORT&&world.at(p)==Type.AIR&&world.at(p.move(0,1,0))==Type.AIR);
    }
    private boolean column(Cell p){return alternatives.values().stream().anyMatch(l->{var c=l.at(1,0,2);return c.x()==p.x()&&c.z()==p.z();});}
    private boolean usable(BanditTowersLayout.Landing l){
        var c=l.at(1,0,2);int feet=l.origin().getY()+4;
        if(feet<=layout.groundY()||world.at(new Cell(c.x(),layout.groundY()-1,c.z()))!=Type.SUPPORT)return false;
        for(int y=layout.groundY();y<feet;y++){var p=new Cell(c.x(),y,c.z());if(world.at(p)!=Type.AIR&&!world.scaffold(p))return false;}
        for(int y=feet;y<=feet+1;y++)if(world.at(new Cell(c.x(),y,c.z()))!=Type.AIR)return false;
        // Cross the native termination joint and proceed two cells into its parent tower/bridge.
        for(int x=0;x>=-2;x--){var p=l.at(x,4,2);
            if(world.at(p.move(0,-1,0))!=Type.SUPPORT||world.at(p)!=Type.AIR||world.at(p.move(0,1,0))!=Type.AIR)return false;
        }
        return true;
    }
    private void features(){for(var f:layout.features())if(!world.feature(f))throw new IllegalStateException("Changed native tower loot or spawner at "+f.pos());}
    boolean step(){
        if(chosen==null){if(!ground.step())return false;chosen=alternatives.get(ground.reachedApproach());features();}
        if(!usable(chosen))throw new IllegalStateException("Tower access changed during construction");
        if(nextY<chosen.origin().getY()+4){var c=chosen.at(1,0,2);world.addScaffold(new Cell(c.x(),nextY++,c.z()));return false;}
        var c=chosen.at(1,0,2);for(int y=layout.groundY();y<nextY;y++)if(!world.scaffold(new Cell(c.x(),y,c.z())))throw new IllegalStateException("Incomplete tower scaffold");
        features();return true;
    }
}
