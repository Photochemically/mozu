package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.InteriorSiteSearch;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.Direction;
import java.util.List;
import java.util.function.BooleanSupplier;

final class GroundHouseRoute {
    interface Probe extends InteriorSiteSearch.Probe {
        boolean spruceDoor(Cell p, Direction facing);
        boolean spruceStair(Cell p, Direction facing);
        boolean oakHalfSlab(Cell p);
        boolean ruinTrigger(Cell ore, Cell carpet, Cell observer, Direction facing, Cell dispenser);
    }

    static BooleanSupplier survey(GroundHouseLayout house, IslandPlan island, int exits, Probe world) {
        // Local Y=0 is the floor, Y=1 the walking surface. Only the perimeter search grows with the island.
        Cell approach;
        switch (house.variant()) {
            case CLEAN -> {
                door(house,world,2,10,Direction.WEST);
                for (int x=3;x<=6;x++) floor(house,world,x,10);
                for (int z=9;z>=3;z--) {
                    if (z==7) door(house,world,6,z,Direction.SOUTH);
                    else floor(house,world,6,z);
                }
                approach=house.at(1,1,10);
            }
            case RUINS -> {
                floor(house,world,2,10);
                var trigger=house.at(3,1,10);
                if (!world.ruinTrigger(trigger.move(0,-1,0),trigger,house.at(4,0,10),house.rotation().rotate(Direction.WEST),house.at(5,0,10)))
                    throw new IllegalStateException("Changed original prairie ruin trigger");
                head(world,trigger,1);
                for (int x=4;x<=6;x++) {
                    var p=house.at(x,1,10);
                    if (!world.oakHalfSlab(p)) throw new IllegalStateException("Missing ruin walkway slab");
                    head(world,p,2);
                }
                floor(house,world,6,9);
                approach=house.at(1,1,10);
            }
            case MUSHROOM -> {
                var stair=house.at(6,0,1);
                if (!world.spruceStair(stair,house.rotation().rotate(Direction.SOUTH)))
                    throw new IllegalStateException("Changed original mushroom house threshold");
                head(world,stair,2);
                for (int z=2;z<=5;z++) floor(house,world,6,z);
                approach=house.at(6,1,0);
            }
            default -> throw new IllegalStateException("Unknown house entrance");
        }
        return new DryPerimeterRoute(island,exits,world,List.of(),List.of(approach))::step;
    }

    private static void door(GroundHouseLayout house, Probe world, int x, int z, Direction facing) {
        var p=house.at(x,1,z);
        if (world.at(p.move(0,-1,0))!=Type.SUPPORT || !world.spruceDoor(p,house.rotation().rotate(facing)))
            throw new IllegalStateException("Incomplete original prairie house door at " + p);
    }
    private static void floor(GroundHouseLayout house, Probe world, int x,int z) {
        var p=house.at(x,1,z);
        var feet=world.at(p);
        if (world.at(p.move(0,-1,0))!=Type.SUPPORT || (feet!=Type.AIR && feet!=Type.FLOOR_DECOR))
            throw new IllegalStateException("Obstructed original house floor at " + p);
        head(world,p,1);
    }
    private static void head(Probe world, Cell base, int height) {
        for (int dy=1;dy<=height;dy++) if (world.at(base.move(0,dy,0))!=Type.AIR)
            throw new IllegalStateException("Insufficient house headroom above " + base);
    }
}
