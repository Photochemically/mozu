package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.InteriorSiteSearch;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.Direction;
import java.util.List;

/** Original west stairs lead into the lower outer hall; upper/interior exploration remains a runtime check. */
final class InfestedRoute {
    interface Probe extends InteriorSiteSearch.Probe {boolean stair(Cell p,Direction facing);boolean feature(InfestedLayout.Feature f);}
    private final InfestedLayout layout;
    private final Probe world;
    private final DryPerimeterRoute ground;
    private boolean connected;
    private int checked;
    InfestedRoute(InfestedLayout layout,IslandPlan island,int exits,Probe world){
        this.layout=layout;this.world=world;
        if(island.firstFreeY()!=layout.groundY())throw new IllegalStateException("Infested temple stairs are off grade");
        entrance();ground=new DryPerimeterRoute(island,exits,world,List.of(),List.of(layout.at(3,1,31)));
    }
    private void entrance(){
        if(world.at(layout.at(3,0,31))!=Type.SUPPORT||!world.stair(layout.at(4,0,31),layout.rotation().rotate(Direction.EAST))
                ||!world.stair(layout.at(5,1,31),layout.rotation().rotate(Direction.EAST)))throw new IllegalStateException("Changed original infested entrance steps");
        for(int x=3;x<=11;x++){
            int feet=x<=4?1:2;
            if(x>=6&&world.at(layout.at(x,1,31))!=Type.SUPPORT)throw new IllegalStateException("Missing original infested hall floor");
            for(int y=feet;y<=4;y++)if(world.at(layout.at(x,y,31))!=Type.AIR)throw new IllegalStateException("Obstructed infested entrance");
        }
        if(!InteriorSiteSearch.clearPlatform(layout.at(9,2,31),world))throw new IllegalStateException("Blocked lower infested hall");
    }
    boolean step(){
        if(!connected){if(!ground.step())return false;connected=true;}
        if(checked<layout.features().size()){var f=layout.features().get(checked++);if(!world.feature(f))throw new IllegalStateException("Changed original infested loot or encounter at "+f.pos());return false;}
        entrance();return true;
    }
}
