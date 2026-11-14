package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.InteriorSiteSearch;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.Direction;
import java.util.*;

/** Add only a supported exterior scaffold; retain the original stone step and balcony doorway. */
final class FortRoute {
    interface Probe extends InteriorSiteSearch.Probe {
        boolean stair(Cell p,Direction direction);
        boolean feature(FortLayout.Feature feature);
        boolean scaffold(Cell p);
        void addScaffold(Cell p);
    }
    private final FortLayout fort;
    private final Probe world;
    private final DryPerimeterRoute ground;
    private final Map<Cell,Integer> sides=new LinkedHashMap<>();
    private Integer chosen;
    private int placed;
    FortRoute(FortLayout fort,IslandPlan island,int exits,Probe world) {
        this.fort=fort;this.world=world;
        if(island.firstFreeY()!=fort.groundY())throw new IllegalStateException("Fort ground is off grade");
        for(int side=0;side<2;side++)if(usable(side))sides.put(fort.side(side,-2,8,24),side);
        ground=new DryPerimeterRoute(island,exits,world,List.of(),sides.keySet(),p->!column(p)
                && world.at(p.move(0,-1,0))==Type.SUPPORT && world.at(p)==Type.AIR && world.at(p.move(0,1,0))==Type.AIR);
    }
    private boolean column(Cell p) {
        for(int side=0;side<2;side++){var c=fort.side(side,-1,8,24);if(p.x()==c.x()&&p.z()==c.z())return true;}
        return false;
    }
    private boolean usable(int side) {
        if(world.at(fort.side(side,-1,7,24))!=Type.SUPPORT)return false;
        for(int y=8;y<=12;y++) {var p=fort.side(side,-1,y,24);if(world.at(p)!=Type.AIR&&!world.scaffold(p))return false;}
        for(int y=13;y<=15;y++)if(world.at(fort.side(side,-1,y,24))!=Type.AIR)return false;
        for(int x=0;x<=8;x++) {
            int feet=x<2?13:12;
            var floor=fort.side(side,x,feet-1,24);
            if(x==0 ? !world.stair(floor,fort.rotation().rotate(side==0?Direction.EAST:Direction.WEST)) : world.at(floor)!=Type.SUPPORT)return false;
            // Extra headroom at the one-block return jump, and over the original threshold.
            for(int y=feet;y<(x<=2?15:14);y++)if(world.at(fort.side(side,x,y,24))!=Type.AIR)return false;
        }
        return true;
    }
    private void features() {for(var f:fort.features())if(!world.feature(f))throw new IllegalStateException("Changed original fort loot or equipment at "+f.pos());}
    boolean step() {
        if(chosen==null) {
            if(!ground.step())return false;
            chosen=sides.get(ground.reachedApproach());features();
        }
        if(!usable(chosen))throw new IllegalStateException("Fort access changed before completion");
        if(placed<5){world.addScaffold(fort.side(chosen,-1,8+placed++,24));return false;}
        for(int y=8;y<=12;y++)if(!world.scaffold(fort.side(chosen,-1,y,24)))throw new IllegalStateException("Incomplete fort scaffold");
        features();return true;
    }
}
