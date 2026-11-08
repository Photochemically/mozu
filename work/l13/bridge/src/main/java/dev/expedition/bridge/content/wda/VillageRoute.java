package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.InteriorSiteSearch;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.Direction;
import java.util.*;

final class VillageRoute {
    interface Probe extends InteriorSiteSearch.Probe {
        boolean door(Cell p,Direction direction);
        boolean halfSlab(Cell p);
        boolean loot(VillageLayout.Loot l);
        boolean water(Cell p);
        boolean spawner(Cell p);
    }
    // Y is in half blocks; the original birch slope climbs from local 1 to local 4.
    private static final int[][] MAIN={{33,2,36},{32,3,36},{32,4,35},{31,5,35},{30,6,35},{29,7,35},
            {29,8,34},{29,8,33},{29,8,32},{28,8,32},{28,8,31},{28,8,30},{28,8,29},{28,8,28}};
    private final VillageLayout village;
    private final IslandPlan island;
    private final int exits;
    private final Probe world;
    private final List<Cell> required=new ArrayList<>();
    private int checked;
    private DryPerimeterRoute ground;
    VillageRoute(VillageLayout village,IslandPlan island,int exits,Probe world) {
        if (island.firstFreeY()!=village.origin().getY()+1) throw new IllegalStateException("Village ground is off grade");
        this.village=village;this.island=island;this.exits=exits;this.world=world;
    }
    boolean step() {
        if (checked<village.houses().size()) {
            var h=village.houses().get(checked++);checkHouse(h);required.add(h.approach());return false;
        }
        if (ground==null) {
            checkMain();checkFeatures();
            ground=new DryPerimeterRoute(island,exits,world,required,List.of(village.at(33,1,36)));
        }
        if (!ground.step()) return false;
        checkMain();checkFeatures();return true;
    }
    private void checkHouse(VillageLayout.House h) {
        var p=h.inlet();var inward=h.facing().getOpposite();
        int steps=h.variant()==7?3:h.variant()==6?4:5;
        for (int i=0;i<steps;i++) {
            var q=p.relative(inward,i);var feet=h.at(q.getX(),1,q.getZ());
            if (world.at(feet.move(0,-1,0))!=Type.SUPPORT) throw new IllegalStateException("Unsupported village house entrance");
            int doorAt=switch(h.variant()){case 0,3,4,5->2;case 2->3;case 6->1;default->-1;};
            if (i==doorAt) {
                if (!world.door(feet,h.rotation().rotate(inward))) throw new IllegalStateException("Broken original birch door");
            } else if (world.at(feet)!=Type.AIR || world.at(feet.move(0,1,0))!=Type.AIR)
                throw new IllegalStateException("Blocked original village house entrance");
        }
    }
    private void checkMain() {
        for (int i=0;i<MAIN.length;i++) {
            var p=MAIN[i];int ceil=(p[1]+1)/2;var floor=village.at(p[0],ceil-1,p[2]);
            if ((p[1]&1)==1?!world.halfSlab(floor):world.at(floor)!=Type.SUPPORT)
                throw new IllegalStateException("Missing original village slope");
            int high=p[1];if (i>0) high=Math.max(high,MAIN[i-1][1]);if (i+1<MAIN.length) high=Math.max(high,MAIN[i+1][1]);
            for (int y=ceil;y<high/2.0+1.8;y++) if (world.at(village.at(p[0],y,p[2]))!=Type.AIR)
                throw new IllegalStateException("Blocked original village slope");
        }
    }
    private void checkFeatures() {
        for (var l:village.loot()) if (!world.loot(l)) throw new IllegalStateException("Changed original village loot");
        for (var p:village.water()) if (!world.water(p)) throw new IllegalStateException("Missing original village water source");
        for (var p:village.spawners()) if (!world.spawner(p)) throw new IllegalStateException("Missing original village encounter");
    }
}
