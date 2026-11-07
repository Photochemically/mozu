package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.DungeonSpec;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import java.util.*;
import java.util.function.BooleanSupplier;

record TempleLayout(int variant,BlockPos origin,Rotation rotation) implements PerimeterLayout {
    static final String TARGET="dungeons_arise:abandoned_temple";
    static final String DIRECTORY=TARGET+"/";
    static final String PREFIX=DIRECTORY+"abandoned_temple_";
    private static final Map<String,BlockPos> SIZES=Map.of(
            "abandoned_road_0",new BlockPos(31,1,31),"abandoned_road_1",new BlockPos(31,1,31),
            "abandoned_road_entrance",new BlockPos(5,1,4),
            "abandoned_temple_deco_0",new BlockPos(3,5,3),"abandoned_temple_deco_1",new BlockPos(3,5,3),
            "abandoned_temple_deco_2",new BlockPos(3,5,3),
            "illusioner_normal_0",new BlockPos(1,3,1),"skeleton_armored_0",new BlockPos(1,3,1),"skeleton_armored_1",new BlockPos(1,3,1));
    record Loot(BlockPos position,String tier) {}
    List<Loot> loot() {
        return variant==0 ? List.of(loot(9,5,23,"entrance"),loot(22,5,24,"entrance"),loot(6,15,6,"map"),loot(25,17,15,"top"))
                : List.of(loot(8,7,10,"entrance"),loot(22,7,20,"entrance"),loot(15,18,19,"top"),loot(15,26,12,"map"));
    }
    private static Loot loot(int x,int y,int z,String tier) { return new Loot(new BlockPos(x,y,z),tier); }

    static TempleLayout inspect(List<WdaTemplatePiece> pieces,WdaStructurePlanner.Environment env) {
        if (env.boarding() || env.entryMode()!=DungeonSpec.EntryMode.PERIMETER
                || (env.surface()!=IslandPlan.Surface.GRASS && env.surface()!=IslandPlan.Surface.ROCK))
            throw new IllegalArgumentException("Abandoned temple requires a dry perimeter");
        if (pieces.isEmpty()) throw new IllegalArgumentException("Missing abandoned temple");
        var root=pieces.get(0);
        int variant;
        if (root.id().equals(PREFIX+"0")) variant=0;
        else if (root.id().equals(PREFIX+"1")) variant=1;
        else throw new IllegalArgumentException("Unexpected abandoned temple root");
        if (root.terrainMatching() || root.origin().getY()!=env.firstFreeY()-3)
            throw new IllegalArgumentException("Abandoned temple root has an unexpected surface height");
        root.requireFrame(root.origin(),root.rotation(),31,48,31);
        var occupied=new HashSet<BlockPos>();
        occupied.add(root.origin());
        for (var p:pieces.subList(1,pieces.size())) {
            if (!p.id().startsWith(DIRECTORY)) throw new IllegalArgumentException("Foreign temple attachment");
            var id=p.id().substring(DIRECTORY.length()); var size=SIZES.get(id);
            if (size==null || !occupied.add(p.origin())) throw new IllegalArgumentException("Unknown or duplicate temple attachment");
            boolean road=id.startsWith("abandoned_road_");
            if (road!=p.terrainMatching() || (road && p.origin().getY()!=env.firstFreeY()))
                throw new IllegalArgumentException("Changed temple road projection or height");
            p.requireFrame(p.origin(),p.rotation(),size.getX(),size.getY(),size.getZ());
        }
        return new TempleLayout(variant,root.origin(),root.rotation());
    }
    BlockPos pos(int x,int y,int z) { return new BlockPos(x,y,z).rotate(rotation).offset(origin); }
    Cell at(int x,int y,int z) {
        var p=pos(x,y,z); return new Cell(p.getX(),p.getY(),p.getZ());
    }
    @Override public TempleLayout move(int x,int y,int z) { return new TempleLayout(variant,origin.offset(x,y,z),rotation); }
    @Override public BooleanSupplier survey(ServerLevel world,IslandPlan island,int exits) {
        return new TempleRoute(this,island,exits,new TempleProbe(world))::step;
    }
}
