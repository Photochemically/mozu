package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.InteriorSiteSearch;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.Direction;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Fixed original footings, including half-block heights; only the ground search scales with the island. */
final class PubRoute {
    interface Probe extends InteriorSiteSearch.Probe {
        boolean stair(Cell p,String kind,Direction facing,String shape);
        boolean slab(Cell p);
        boolean loot(PubLayout.Loot expected);
    }
    // Y is in half-block units. The original route rises from local 20 to local 26.
    private static final int[][] PATH={{2,40,22},{3,42,22},{4,42,22},{5,44,22},{6,44,22},{7,44,22},
            {8,46,22},{9,46,22},{10,48,22},{11,48,22},{12,48,22},{13,48,22},{14,48,22},
            {14,48,21},{14,48,20},{14,48,19},{14,49,18},{14,49,17},{15,50,17},{16,51,17},
            {17,52,17},{17,52,16},{17,52,15},{17,52,14},{17,52,13}};
    static BooleanSupplier survey(PubLayout pub,IslandPlan island,int exits,Probe world) {
        if (island.firstFreeY()!=pub.groundY()) throw new IllegalStateException("Pub ground is off grade");
        check(pub,world);
        var ground=new DryPerimeterRoute(island,exits,world,List.of(),List.of(pub.at(2,20,22)));
        return () -> { if (!ground.step()) return false; check(pub,world); return true; };
    }
    private static void check(PubLayout pub,Probe world) {
        for (int i=0;i<PATH.length;i++) {
            var p=PATH[i]; int ceil=(p[1]+1)/2;
            var floor=pub.at(p[0],ceil-1,p[2]);
            boolean footing;
            if ((p[1]&1)!=0) footing=world.slab(floor);
            else if (i==1||i==3||i==4||i==6||i==8) {
                String shape=i==3?"outer_right":i==4?"inner_left":"straight";
                var direction=i==3?Direction.NORTH:Direction.EAST;
                footing=world.stair(floor,i==8?"dark_prismarine_stairs":"stone_stairs",pub.rotation().rotate(direction),shape);
            } else footing=world.at(floor)==Type.SUPPORT;
            if (!footing) throw new IllegalStateException("Changed original pub footing at "+floor);
            int high=p[1];
            if (i>0) high=Math.max(high,PATH[i-1][1]);
            if (i+1<PATH.length) high=Math.max(high,PATH[i+1][1]);
            // 1.8-block standing clearance, extended to the higher neighboring footing for a return jump.
            for (int y=ceil;y<(high/2.0)+1.8;y++) if (world.at(pub.at(p[0],y,p[2]))!=Type.AIR)
                throw new IllegalStateException("Blocked original pub entrance");
        }
        for (var l:pub.loot()) if (!world.loot(l)) throw new IllegalStateException("Changed original pub loot at "+l.position());
    }
}
