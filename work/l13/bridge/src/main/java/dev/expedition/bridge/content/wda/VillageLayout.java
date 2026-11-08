package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.DungeonSpec;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import java.util.*;
import java.util.function.BooleanSupplier;

record VillageLayout(BlockPos origin,Rotation rotation,List<House> houses) implements PerimeterLayout {
    static final String TARGET="dungeons_arise:mushroom_village";
    static final String PREFIX=TARGET+"/mushroom_village_house_";
    private record Socket(BlockPos pos,Direction facing) {}
    private static Socket socket(int x,int z,Direction d) {return new Socket(new BlockPos(x,1,z),d);}
    private static final List<Socket> SOCKETS=List.of(
            socket(0,9,Direction.WEST),socket(0,28,Direction.WEST),socket(0,37,Direction.WEST),
            socket(9,0,Direction.NORTH),socket(25,0,Direction.NORTH),socket(37,0,Direction.NORTH),
            socket(9,44,Direction.SOUTH),socket(22,44,Direction.SOUTH),socket(37,44,Direction.SOUTH),
            socket(44,8,Direction.EAST),socket(44,22,Direction.EAST),socket(44,35,Direction.EAST));
    record House(int variant,BlockPos origin,Rotation rotation) {
        Cell at(int x,int y,int z) {return point(origin,rotation,x,y,z);}
        BlockPos inlet() {return variant==3?new BlockPos(5,1,0):variant==1||variant==4?new BlockPos(10,1,5):
                variant==7?new BlockPos(0,1,5):new BlockPos(5,1,10);}
        Direction facing() {return variant==3?Direction.NORTH:variant==1||variant==4?Direction.EAST:
                variant==7?Direction.WEST:Direction.SOUTH;}
        Cell approach() {var p=inlet().relative(facing());return at(p.getX(),p.getY(),p.getZ());}
    }
    record Loot(Cell pos,String kind,String tier) {}
    static VillageLayout inspect(List<WdaTemplatePiece> pieces,WdaStructurePlanner.Environment env) {
        if (env.boarding() || env.entryMode()!=DungeonSpec.EntryMode.PERIMETER
                || (env.surface()!=IslandPlan.Surface.GRASS && env.surface()!=IslandPlan.Surface.ROCK))
            throw new IllegalArgumentException("Mushroom village requires a dry perimeter");
        if (pieces.isEmpty() || pieces.size()>13 || !pieces.get(0).id().equals(PREFIX+"big_0"))
            throw new IllegalArgumentException("Missing original mushroom village center");
        var root=pieces.get(0);
        if (root.origin().getY()!=env.firstFreeY()-1) throw new IllegalArgumentException("Village is off grade");
        root.requireFrame(root.origin(),root.rotation(),45,45,45);
        var houses=new ArrayList<House>();var used=new HashSet<Socket>();
        for (int i=0;i<pieces.size();i++) {
            var p=pieces.get(i);
            if (p.terrainMatching()) throw new IllegalArgumentException("Village pieces must remain rigid");
            // Native siblings cannot overlap. This also keeps location-based mushroom recoloring away from earlier pieces.
            for (int j=0;j<i;j++) {
                var a=p.bounds();var c=pieces.get(j).bounds();
                if (a.minX()<=c.maxX() && a.maxX()>=c.minX() && a.minY()<=c.maxY() && a.maxY()>=c.minY()
                        && a.minZ()<=c.maxZ() && a.maxZ()>=c.minZ())
                    throw new IllegalArgumentException("Overlapping village pieces");
            }
            if (i==0) continue;
            if (!p.id().matches(PREFIX+"small_[0-7]")) throw new IllegalArgumentException("Unknown village attachment");
            int variant=p.id().charAt(p.id().length()-1)-'0';
            var h=new House(variant,p.origin(),p.rotation());
            p.requireFrame(p.origin(),p.rotation(),variant==3?12:11,variant==4?21:16,11);
            var inlet=h.inlet();var actual=inlet.rotate(h.rotation()).offset(h.origin());
            var socket=SOCKETS.stream().filter(s->s.pos().relative(s.facing()).rotate(root.rotation()).offset(root.origin()).equals(actual)
                    && root.rotation().rotate(s.facing()).getOpposite()==h.rotation().rotate(h.facing())).findFirst().orElseThrow(
                            ()->new IllegalArgumentException("Detached village house"));
            if (!used.add(socket)) throw new IllegalArgumentException("Duplicate village connection");
            houses.add(h);
        }
        return new VillageLayout(root.origin(),root.rotation(),List.copyOf(houses));
    }
    Cell at(int x,int y,int z) {return point(origin,rotation,x,y,z);}
    static Cell point(BlockPos origin,Rotation rotation,int x,int y,int z) {
        var p=new BlockPos(x,y,z).rotate(rotation).offset(origin);return new Cell(p.getX(),p.getY(),p.getZ());
    }
    List<Loot> loot() {
        var out=new ArrayList<Loot>(List.of(new Loot(at(33,4,22),"chest","treasure"),new Loot(at(11,7,22),"chest","treasure"),
                new Loot(at(22,18,28),"chest","treasure"),new Loot(at(19,7,20),"barrel","barrels")));
        for (var h:houses) switch(h.variant()) {
            case 0 -> out.add(new Loot(h.at(7,1,5),"barrel","barrels"));
            case 3 -> {out.add(new Loot(h.at(7,1,6),"barrel","barrels"));out.add(new Loot(h.at(7,7,5),"chest","treasure"));}
            case 4 -> out.add(new Loot(h.at(6,8,3),"barrel","barrels"));
            case 5 -> out.add(new Loot(h.at(4,1,6),"barrel","barrels"));
            case 6 -> out.add(new Loot(h.at(8,0,1),"barrel","barrels"));
        }
        return List.copyOf(out);
    }
    List<Cell> water() {
        var out=new ArrayList<Cell>(List.of(at(30,0,5),at(34,0,8),at(36,0,24),at(38,0,10),at(38,0,31)));
        for (var h:houses) {
            if (h.variant()==5) out.add(h.at(2,0,8));
            if (h.variant()==7) for (var p:List.of(new int[]{4,5},new int[]{4,6},new int[]{6,3},new int[]{7,5},new int[]{8,4},new int[]{8,5}))
                out.add(h.at(p[0],1,p[1]));
        }
        return List.copyOf(out);
    }
    List<Cell> spawners() {return List.of(at(26,4,29),at(14,7,25),at(22,18,24));}
    List<BlockPos> turtles() {return houses.stream().filter(h->h.variant()==7).map(h->{
        var p=h.at(4,1,6);return new BlockPos(p.x(),p.y(),p.z());
    }).toList();}
    boolean matchesTurtles(List<BlockPos> actual) {return actual.size()==turtles().size() && new HashSet<>(actual).equals(new HashSet<>(turtles()));}
    @Override public VillageLayout move(int x,int y,int z) {
        return new VillageLayout(origin.offset(x,y,z),rotation,houses.stream().map(h->new House(h.variant(),h.origin().offset(x,y,z),h.rotation())).toList());
    }
    @Override public BooleanSupplier survey(ServerLevel world,IslandPlan island,int exits) {
        return new VillageRoute(this,island,exits,new VillageProbe(world))::step;
    }
}
