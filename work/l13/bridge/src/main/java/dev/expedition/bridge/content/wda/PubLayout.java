package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.DungeonSpec;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import java.util.*;
import java.util.function.BooleanSupplier;

record PubLayout(BlockPos origin,Rotation rotation,List<Loot> loot) implements PerimeterLayout {
    static final String TARGET="dungeons_arise:greenwood_pub";
    static final String PREFIX=TARGET+"/greenwood_pub_";
    record Loot(BlockPos position,String kind,String table) {}
    private static BlockPos size(String id) {
        if (id.equals("0")) return new BlockPos(31,48,31);
        if (id.matches("furniture_(0|[2-9]|1[0-6])")) return new BlockPos(2,2,2);
        if (id.equals("hallway_0")) return new BlockPos(9,5,8);
        if (id.matches("hallway_[2-6]")) return new BlockPos(7,5,5);
        if (id.matches("room_[0-4]")) return new BlockPos(10,6,9);
        if (id.matches("spawner_[01]")) return new BlockPos(1,2,1);
        return switch(id) {
            case "stairs_0" -> new BlockPos(7,8,12);
            case "stairs_1" -> new BlockPos(12,8,5);
            case "stairs_2" -> new BlockPos(10,12,9);
            case "terminator_0" -> new BlockPos(1,3,3);
            default -> throw new IllegalArgumentException("Unknown or unreachable original pub piece "+id);
        };
    }
    static PubLayout inspect(List<WdaTemplatePiece> pieces,WdaStructurePlanner.Environment env,List<Loot> loot) {
        if (env.boarding() || env.entryMode()!=DungeonSpec.EntryMode.PERIMETER
                || (env.surface()!=IslandPlan.Surface.GRASS && env.surface()!=IslandPlan.Surface.ROCK))
            throw new IllegalArgumentException("Greenwood pub requires a dry perimeter");
        if (pieces.size()<2) throw new IllegalArgumentException("Missing pub cellar connection");
        var root=pieces.get(0);
        if (!root.id().equals(PREFIX+"0") || root.origin().getY()!=env.firstFreeY()-19)
            throw new IllegalArgumentException("Unexpected pub root or surface height");
        var occupied=new HashSet<BlockPos>();
        WdaTemplatePiece cellar=null;
        var cellarOrigin=new BlockPos(20,-4,-1).rotate(root.rotation()).offset(root.origin());
        for (int i=0;i<pieces.size();i++) {
            var p=pieces.get(i);
            if (!p.id().startsWith(PREFIX) || p.terrainMatching() || !occupied.add(p.origin()))
                throw new IllegalArgumentException("Unknown, projected or duplicate pub attachment");
            var id=p.id().substring(PREFIX.length());
            if (i>0 && id.equals("0")) throw new IllegalArgumentException("Duplicate pub body");
            var s=size(id); p.requireFrame(p.origin(),p.rotation(),s.getX(),s.getY(),s.getZ());
            if (p.origin().equals(cellarOrigin)) cellar=p;
        }
        if (cellar==null || !cellar.id().equals(PREFIX+"stairs_2"))
            throw new IllegalArgumentException("Missing original cellar stairs");
        cellar.requireFrame(cellarOrigin,root.rotation().getRotated(Rotation.COUNTERCLOCKWISE_90),10,12,9);
        if (loot.size()<21 || loot.stream().map(Loot::position).distinct().count()!=loot.size())
            throw new IllegalArgumentException("Missing or overlapping original pub loot");
        return new PubLayout(root.origin(),root.rotation(),List.copyOf(loot));
    }
    int groundY() { return Math.addExact(origin.getY(),20); }
    Cell at(int x,int y,int z) {
        var p=new BlockPos(x,y,z).rotate(rotation).offset(origin);
        return new Cell(p.getX(),p.getY(),p.getZ());
    }
    @Override public PubLayout move(int x,int y,int z) {
        return new PubLayout(origin.offset(x,y,z),rotation,loot.stream()
                .map(l->new Loot(l.position().offset(x,y,z),l.kind(),l.table())).toList());
    }
    @Override public BooleanSupplier survey(ServerLevel world,IslandPlan island,int exits) {
        return PubRoute.survey(this,island,exits,new PubProbe(world));
    }
}
