package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.InteriorSiteSearch;
import dev.expedition.bridge.domain.IslandPlan;
import java.util.*;

/** An original covered outer arcade is the exploration start, not proof of the entire arena route. */
final class ColiseumRoute {
    interface Probe extends InteriorSiteSearch.Probe { boolean device(ColiseumLayout.Device device); }
    private final ColiseumLayout layout;
    private final Probe world;
    private final DryPerimeterRoute ground;
    private final Map<Cell,Integer> arches=new LinkedHashMap<>();
    ColiseumRoute(ColiseumLayout layout,IslandPlan island,int exits,Probe world){
        this.layout=layout;this.world=world;
        if(island.firstFreeY()!=layout.groundY())throw new IllegalStateException("Coliseum arcade is off grade");
        for(int side=0;side<4;side++)if(usable(side))arches.put(layout.arch(side,4),side);
        ground=new DryPerimeterRoute(island,exits,world,List.of(),arches.keySet());
    }
    private boolean usable(int side){
        for(int along=0;along<=4;along++){
            var p=layout.arch(side,along);
            if(world.at(p.move(0,-1,0))!=Type.SUPPORT)return false;
            for(int h=0;h<3;h++)if(world.at(p.move(0,h,0))!=Type.AIR)return false;
        }
        return InteriorSiteSearch.clearPlatform(layout.arch(side,3),world);
    }
    boolean step(){
        if(!ground.step())return false;
        if(!usable(arches.get(ground.reachedApproach())))throw new IllegalStateException("Coliseum arch changed during survey");
        if(!world.device(layout.spawner()))throw new IllegalStateException("Changed original coliseum spawner");
        return true;
    }
}
