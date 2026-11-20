package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import java.util.*;

/** Supported climb to an original broken bridge or the side of a capped opening; no carving. */
final class ThornbornRoute {
    interface Probe extends InteriorSiteSearch.Probe {
        boolean feature(ThornbornLayout.Feature f);boolean bottomSlab(Cell p);boolean scaffold(Cell p);void addScaffold(Cell p);
    }
    private final ThornbornLayout layout;private final Probe world;private final DryPerimeterRoute ground;
    private final Map<Cell,ThornbornLayout.Landing> alternatives=new LinkedHashMap<>();
    private ThornbornLayout.Landing chosen;private int checked,nextY;
    ThornbornRoute(ThornbornLayout layout,IslandPlan island,int exits,Probe world){this.layout=layout;this.world=world;nextY=layout.groundY();
        if(island.firstFreeY()!=layout.groundY())throw new IllegalStateException("Thornborn ground must remain below every original component");
        for(var l:layout.landings())if(usable(l))alternatives.put(l.approach(layout.groundY()),l);
        ground=new DryPerimeterRoute(island,exits,world,List.of(),alternatives.keySet(),p->!column(p)&&floor(p)&&air(p)&&air(p.move(0,1,0)));
    }
    private boolean air(Cell p){return world.at(p)==Type.AIR;}
    private boolean floor(Cell feet){return world.at(feet.move(0,-1,0))==Type.SUPPORT;}
    private boolean walk(Cell p){return floor(p)&&air(p)&&air(p.move(0,1,0));}
    private boolean column(Cell p){return alternatives.values().stream().anyMatch(l->l.column().x()==p.x()&&l.column().z()==p.z());}
    private boolean usable(ThornbornLayout.Landing l){var c=l.column();int feet=l.feet();
        if(feet<=layout.groundY()||world.at(new Cell(c.x(),layout.groundY()-1,c.z()))!=Type.SUPPORT)return false;
        for(int y=layout.groundY();y<feet;y++){var p=new Cell(c.x(),y,c.z());if(!air(p)&&!world.scaffold(p))return false;}
        if(!air(new Cell(c.x(),feet,c.z()))||!air(new Cell(c.x(),feet+1,c.z())))return false;
        if(l.broken()){
            for(int z=6;z<=7;z++)if(!world.bottomSlab(l.at(9,11,z))||!air(l.at(9,12,z))||!air(l.at(9,13,z)))return false;
            for(int x=9;x<=19;x++)if(!walk(l.at(x,12,8)))return false;
        }else{
            // Side top stair avoids the original central flowerpot. One-block descent is reversible by jumping.
            if(!walk(l.at(0,2,0))||!air(l.at(1,3,0)))return false;
            for(int x=1;x<=3;x++)if(!walk(l.at(x,1,0)))return false;
        }
        return true;
    }
    boolean step(){
        if(chosen==null){if(!ground.step())return false;chosen=alternatives.get(ground.reachedApproach());}
        if(checked<layout.features().size()){var f=layout.features().get(checked++);if(!world.feature(f))throw new IllegalStateException("Changed thornborn loot or encounter at "+f.pos());return false;}
        if(!usable(chosen))throw new IllegalStateException("Thornborn original access changed during construction");
        if(nextY<chosen.feet()){var c=chosen.column();world.addScaffold(new Cell(c.x(),nextY++,c.z()));return false;}
        var c=chosen.column();for(int y=layout.groundY();y<nextY;y++)if(!world.scaffold(new Cell(c.x(),y,c.z())))throw new IllegalStateException("Incomplete thornborn supported climb");return true;
    }
}
