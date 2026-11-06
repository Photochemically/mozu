package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.DungeonSpec;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import java.util.*;

record MerchantCampLayout(Cell chest, List<Cell> chestApproaches, List<Tent> tents) {
    static final String TARGET = "dungeons_arise:merchant_campsite";
    static final String PREFIX = TARGET + "/merchant_campsite_";
    private static final String ROOT = PREFIX + "street_4_main";
    private static final Set<String> STREETS = Set.of("street_0","street_1","street_2","street_3","street_4_main");

    // Aisles are in local coordinates. The two open tents have an oak fence at the central socket.
    enum Aisle {
        SMALL(2,0,3,true), WORKSHOP(5,0,7,true), BEDS(5,0,4,false), LONG_BEDS(3,6,2,false);
        final int x, first, last, direction;
        final boolean door;
        Aisle(int x, int first, int last, boolean door) {
            this.x=x; this.first=first; this.last=last; this.door=door;
            direction=Integer.signum(last-first);
        }
    }
    record Tent(BlockPos origin, Rotation rotation, Aisle aisle) {
        Cell at(int z) { return point(origin,rotation,aisle.x,0,z); }
        Cell approach() { return at(aisle.first-aisle.direction); }
    }

    static MerchantCampLayout inspect(List<WdaTemplatePiece> pieces, WdaStructurePlanner.Environment env) {
        if (env.entryMode()!=DungeonSpec.EntryMode.PERIMETER || env.boarding()
                || (env.surface()!=IslandPlan.Surface.GRASS && env.surface()!=IslandPlan.Surface.ROCK))
            throw new IllegalArgumentException("Merchant campsite requires a dry perimeter");
        if (pieces.isEmpty() || !pieces.get(0).id().equals(ROOT)) throw new IllegalArgumentException("Missing original merchant campsite center");
        var root=pieces.get(0);
        var tents=new ArrayList<Tent>();
        int centers=0;
        for (var piece:pieces) {
            if (!piece.id().startsWith(PREFIX)) throw new IllegalArgumentException("Unexpected campsite template " + piece.id());
            var id=piece.id().substring(PREFIX.length());
            if (STREETS.contains(id)!=piece.terrainMatching()) throw new IllegalArgumentException("Changed campsite projection " + id);
            if (piece.id().equals(ROOT)) centers++;
            if (STREETS.contains(id) || id.equals("villager_normal_0") || id.equals("iron_golem_normal_0")) continue;
            var aisle=switch(id) {
                case "tent_0" -> Aisle.SMALL;
                case "tent_1" -> Aisle.WORKSHOP;
                case "tent_2" -> Aisle.BEDS;
                case "tent_3" -> Aisle.LONG_BEDS;
                default -> throw new IllegalArgumentException("Template outside the campsite pools " + id);
            };
            if (piece.origin().getY()!=env.firstFreeY()) throw new IllegalArgumentException("Rigid tent does not meet projected street");
            tents.add(new Tent(piece.origin(),piece.rotation(),aisle));
        }
        if (centers!=1 || tents.isEmpty()) throw new IllegalArgumentException("Campsite needs its original center and at least one tent");
        var projected=new BlockPos(root.origin().getX(),env.firstFreeY()-1,root.origin().getZ());
        return new MerchantCampLayout(point(projected,root.rotation(),6,1,6),
                List.of(point(projected,root.rotation(),6,1,5),point(projected,root.rotation(),5,1,6)), List.copyOf(tents));
    }

    private static Cell point(BlockPos origin, Rotation rotation, int x, int y, int z) {
        var p = new BlockPos(x,y,z).rotate(rotation).offset(origin);
        return new Cell(p.getX(),p.getY(),p.getZ());
    }

    MerchantCampLayout move(int dx,int dy,int dz) {
        return new MerchantCampLayout(chest.move(dx,dy,dz),chestApproaches.stream().map(c->c.move(dx,dy,dz)).toList(),
                tents.stream().map(t->new Tent(t.origin.offset(dx,dy,dz),t.rotation,t.aisle)).toList());
    }
}
