package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.Direction;
import java.util.List;

/** Two supported ends and a seven-cell span; reaches the original northern garden without carving. */
final class ShirazRoute {
    interface Probe extends InteriorSiteSearch.Probe {
        boolean stair(Cell p,Direction facing,String shape);
        boolean feature(ShirazLayout.Feature f);
        boolean scaffold(Cell p,int distance);
        void addScaffold(Cell p,int distance);
    }
    static final int[] ORDER={-1,7,0,6,1,5,2,4,3};
    static int distance(int z){return Math.min(z+1,7-z);}
    private final ShirazLayout layout;private final Probe world;private final DryPerimeterRoute ground;
    private int checked,placed;private boolean connected;
    ShirazRoute(ShirazLayout layout,IslandPlan island,int exits,Probe world){
        this.layout=layout;this.world=world;
        if(island.firstFreeY()!=layout.groundY())throw new IllegalStateException("Palace garden ground is off grade");
        entrance();ground=new DryPerimeterRoute(island,exits,world,List.of(),List.of(layout.at(71,33,-2)));
    }
    private void entrance(){
        for(int z:new int[]{-1,7,10})if(world.at(layout.at(71,32,z))!=Type.SUPPORT)throw new IllegalStateException("Missing palace bridge support or garden floor");
        if(!world.stair(layout.at(71,32,8),layout.rotation().rotate(Direction.EAST),"outer_right")
                ||!world.stair(layout.at(71,32,9),layout.rotation().rotate(Direction.SOUTH),"inner_left"))throw new IllegalStateException("Changed original waterlogged palace stairs");
        for(int z=-1;z<=10;z++)for(int y=33;y<=36;y++){
            var p=layout.at(71,y,z);if(world.at(p)==Type.AIR)continue;
            if(y==33&&z<=7&&world.scaffold(p,distance(z)))continue;
            throw new IllegalStateException("Obstructed original palace garden approach");
        }
        for(int z=0;z<=6;z++)if(world.at(layout.at(71,32,z))!=Type.AIR)throw new IllegalStateException("Original palace perimeter gap changed");
    }
    boolean step(){
        if(!connected){if(!ground.step())return false;connected=true;}
        if(checked<layout.features().size()){var f=layout.features().get(checked++);if(!world.feature(f))throw new IllegalStateException("Changed original palace loot, fluid or encounter at "+f.pos());return false;}
        entrance();
        if(placed<ORDER.length){int z=ORDER[placed++];world.addScaffold(layout.at(71,33,z),distance(z));return false;}
        for(int z=-1;z<=7;z++)if(!world.scaffold(layout.at(71,33,z),distance(z)))throw new IllegalStateException("Incomplete supported palace bridge");
        return true;
    }
}
