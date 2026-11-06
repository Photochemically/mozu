package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.DungeonSpec;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.InteriorSiteSearch;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.function.BooleanSupplier;

record IllagerCampLayout(Cell mapChest, List<Cell> tentInteriors) implements PerimeterLayout {
    static final String TARGET = "dungeons_arise:illager_campsite";
    static final String PREFIX = TARGET + "/illager_campsite_";
    private static final String CENTER = "street_6_main";
    private static final Set<String> STREETS = Set.of(CENTER,"street_0","street_1","street_2","street_3","street_4","street_5");
    private static final Set<String> ATTACHMENTS = Set.of("deco_0","deco_1","deco_2","deco_3","deco_4","deco_5","deco_6",
            "vindicator_normal_0","pillager_normal_0","pillager_multishot_0");
    private static final Map<String,BlockPos> TENTS = Map.of("tent_0",new BlockPos(5,0,5), "tent_1",new BlockPos(4,0,3),
            "tent_2",new BlockPos(2,0,2), "tent_3",new BlockPos(6,0,5));

    static IllagerCampLayout inspect(List<WdaTemplatePiece> pieces, WdaStructurePlanner.Environment env) {
        if (env.entryMode()!=DungeonSpec.EntryMode.PERIMETER || env.boarding()
                || (env.surface()!=IslandPlan.Surface.GRASS && env.surface()!=IslandPlan.Surface.ROCK))
            throw new IllegalArgumentException("Illager camp requires a dry perimeter");
        if (pieces.isEmpty() || !pieces.get(0).id().equals(PREFIX+CENTER))
            throw new IllegalArgumentException("Missing original illager camp center");
        var root=pieces.get(0);
        var chestOffset=new BlockPos(7,0,7).rotate(root.rotation());
        var chest=new Cell(root.origin().getX()+chestOffset.getX(),env.firstFreeY(),root.origin().getZ()+chestOffset.getZ());
        var goals=new ArrayList<Cell>();
        int streets=0;
        for (var p:pieces) {
            if (!p.id().startsWith(PREFIX)) throw new IllegalArgumentException("Unknown illager camp piece " + p.id());
            String id=p.id().substring(PREFIX.length());
            if (STREETS.contains(id)!=p.terrainMatching()) throw new IllegalArgumentException("Unexpected camp projection " + id);
            if (STREETS.contains(id)) {
                if (CENTER.equals(id) && streets!=0) throw new IllegalArgumentException("Duplicate illager camp center");
                streets++;
            } else if (!ATTACHMENTS.contains(id)) {
                var goal=TENTS.get(id);
                if (goal==null || p.origin().getY()!=env.firstFreeY()) throw new IllegalArgumentException("Unknown or misaligned tent " + id);
                var interior=goal.rotate(p.rotation()).offset(p.origin());
                goals.add(new Cell(interior.getX(),interior.getY(),interior.getZ()));
            }
        }
        // street_5 carries patrol sockets but no tent socket. Preserve such native layouts.
        if (streets<2) throw new IllegalArgumentException("Illager camp center has no attached street");
        return new IllagerCampLayout(chest,List.copyOf(goals));
    }

    @Override public IllagerCampLayout move(int x,int y,int z) {
        return new IllagerCampLayout(mapChest.move(x,y,z),tentInteriors.stream().map(p->p.move(x,y,z)).toList());
    }

    interface Probe extends InteriorSiteSearch.Probe {
        boolean dirtPath(Cell floor);
        boolean signalFire(Cell p);
        boolean chest(Cell p);
    }

    @Override public BooleanSupplier survey(ServerLevel world,IslandPlan island,int exits) {
        return survey(new IllagerCampProbe(world),island,exits);
    }

    BooleanSupplier survey(Probe world,IslandPlan island,int exits) {
        if (!world.chest(mapChest) || world.at(mapChest.move(0,1,0))!=Type.AIR)
            throw new IllegalStateException("Original illager camp map chest missing or covered");
        var outside=new ArrayList<Cell>();
        for (var direction:List.of(new int[]{-1,0},new int[]{1,0},new int[]{0,-1},new int[]{0,1})) {
            int x=direction[0],z=direction[1];
            if (!world.signalFire(mapChest.move(x,0,z))) throw new IllegalStateException("Incomplete original signal fire ring");
            outside.add(mapChest.move(3*x,0,3*z));
        }
        var route=new DryPerimeterRoute(island,exits,world,tentInteriors,outside,p->{
            var feet=world.at(p);
            if ((feet!=Type.AIR && feet!=Type.FLOOR_DECOR) || world.at(p.move(0,1,0))!=Type.AIR) return false;
            var floor=p.move(0,-1,0);
            return world.at(floor)==Type.SUPPORT || world.dirtPath(floor);
        });
        return route::step;
    }
}
