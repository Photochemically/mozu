package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.InteriorSiteSearch;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.Direction;
import java.util.*;

final class TempleRoute {
    interface Probe extends InteriorSiteSearch.Probe {
        boolean stair(Cell p,Direction facing);
        boolean gravel(Cell p);
        boolean chest(Cell p,String tier);
        boolean scaffold(Cell p);
        void addScaffold(Cell p);
    }
    private final TempleLayout temple;
    private final Probe world;
    private final DryPerimeterRoute ground;
    private final Map<Cell,Integer> sides=new LinkedHashMap<>();
    private Integer side;
    private int placed;
    private boolean surveyed;
    private static final int[][] STAIRS={{12,3,26},{12,2,25},{13,1,25},{14,1,25},{15,1,25},
            {15,2,24},{15,3,23},{15,4,22},{15,5,21},{15,5,20},{15,5,19},{15,5,18}};

    TempleRoute(TempleLayout temple,IslandPlan island,int exits,Probe world) {
        this.temple=temple; this.world=world;
        if (island.firstFreeY()!=temple.origin().getY()+3) throw new IllegalStateException("Temple is off grade");
        if (temple.variant()==0) {
            checkStairs();
            sides.put(temple.at(12,3,26),0);
        } else for (int x:List.of(3,27)) if (sideUsable(x)) sides.put(temple.at(x==3?2:28,3,15),x);
        // Keep the planned scaffold columns outside the ground walk graph, including a repeated survey.
        ground=new DryPerimeterRoute(island,exits,world,List.of(),sides.keySet(),p ->
                !inColumn(p) && (world.at(p.move(0,-1,0))==Type.SUPPORT || world.gravel(p.move(0,-1,0)))
                        && world.at(p)==Type.AIR && world.at(p.move(0,1,0))==Type.AIR);
    }

    private boolean inColumn(Cell p) {
        if (temple.variant()==0) return false;
        for (int x:List.of(3,27)) { var col=temple.at(x,3,15); if (p.x()==col.x() && p.z()==col.z()) return true; }
        return false;
    }
    boolean step() {
        if (!surveyed) {
            if (!ground.step()) return false;
            side=sides.get(ground.reachedApproach());
            checkLoot();
            if (temple.variant()==0) checkStairs();
            else if (!sideUsable(side)) throw new IllegalStateException("Temple scaffold approach changed");
            surveyed=true;
        }
        if (temple.variant()==1 && placed<5) {
            world.addScaffold(temple.at(side,3+placed,15));
            placed++;
            return false;
        }
        if (temple.variant()==1) {
            for (int y=3;y<=7;y++) if (!world.scaffold(temple.at(side,y,15)))
                throw new IllegalStateException("Incomplete temple scaffold");
            if (!sideUsable(side)) throw new IllegalStateException("Temple landing changed");
        } else checkStairs();
        checkLoot();
        return true;
    }

    private void checkStairs() {
        for (int i=0;i<STAIRS.length;i++) {
            var p=STAIRS[i]; var feet=temple.at(p[0],p[1],p[2]); var floor=feet.move(0,-1,0);
            boolean stair=i>=2 && i<=8;
            if (stair ? !world.stair(floor,temple.rotation().rotate(Direction.NORTH)) : world.at(floor)!=Type.SUPPORT)
                throw new IllegalStateException("Missing temple entrance footing");
            boolean jump=i>0 && STAIRS[i-1][1]>p[1] || i+1<STAIRS.length && STAIRS[i+1][1]>p[1];
            for (int y=0;y<=(jump?3:2);y++) if (world.at(feet.move(0,y,0))!=Type.AIR)
                throw new IllegalStateException("Blocked temple stair entrance");
        }
    }
    private boolean sideUsable(int x) {
        if (world.at(temple.at(x,2,15))!=Type.SUPPORT) return false;
        for (int y=3;y<=7;y++) {
            var p=temple.at(x,y,15);
            if (world.at(p)!=Type.AIR && !world.scaffold(p)) return false;
        }
        for (int y=8;y<=10;y++) if (world.at(temple.at(x,y,15))!=Type.AIR) return false;
        int direction=x==3?1:-1;
        for (int offset=1;offset<=12;offset++) {
            int feetY=offset==1?8:7;
            var feet=temple.at(x+direction*offset,feetY,15);
            if (world.at(feet.move(0,-1,0))!=Type.SUPPORT) return false;
            for (int y=0;y<=2;y++) if (world.at(feet.move(0,y,0))!=Type.AIR) return false;
        }
        return true;
    }
    private void checkLoot() {
        for (var loot:temple.loot()) {
            var p=loot.position();
            if (!world.chest(temple.at(p.getX(),p.getY(),p.getZ()),loot.tier()))
                throw new IllegalStateException("Changed original temple loot");
        }
    }
}
