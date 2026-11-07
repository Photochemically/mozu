package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.DungeonSpec;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import java.util.*;
import java.util.function.BooleanSupplier;

record TreeHouseLayout(BlockPos origin, Rotation rotation, List<Spawn> inhabitants) implements PerimeterLayout {
    static final String TARGET="dungeons_arise:jungle_tree_house";
    static final String PREFIX=TARGET+"/jungle_tree_house_";
    private record Socket(BlockPos pos,Rotation rotation) {}
    private static Socket socket(int x,int y,int z,Rotation r) { return new Socket(new BlockPos(x,y,z),r); }
    private static final List<Socket> SOCKETS=List.of(
            socket(15,15,6,Rotation.NONE), socket(13,24,7,Rotation.COUNTERCLOCKWISE_90),
            socket(17,24,7,Rotation.COUNTERCLOCKWISE_90), socket(24,24,13,Rotation.NONE), socket(24,24,17,Rotation.NONE),
            socket(11,30,10,Rotation.NONE), socket(15,30,21,Rotation.CLOCKWISE_180), socket(21,30,15,Rotation.CLOCKWISE_180),
            socket(11,35,17,Rotation.CLOCKWISE_90), socket(14,35,11,Rotation.CLOCKWISE_90), socket(19,35,15,Rotation.CLOCKWISE_90));
    record Spawn(BlockPos position,String type) {}
    record Loot(BlockPos position,String kind,String table) {}
    static final List<Loot> LOOT=List.of(
            loot(21,24,20,"barrel","barrels"),loot(4,26,24,"barrel","barrels"),
            loot(9,30,15,"chest","normal"),loot(9,30,20,"barrel","barrels"),
            loot(10,30,21,"barrel","barrels"),loot(21,30,9,"barrel","barrels"),loot(22,32,9,"barrel","barrels"),
            loot(19,35,13,"barrel","barrels"),loot(15,35,15,"chest","treasure"));
    private static Loot loot(int x,int y,int z,String kind,String tier) {
        return new Loot(new BlockPos(x,y,z),kind,"dungeons_arise:chests/jungle_tree_house/jungle_tree_house_"+tier);
    }

    static TreeHouseLayout inspect(List<WdaTemplatePiece> pieces,WdaStructurePlanner.Environment env) {
        if (env.boarding() || env.entryMode()!=DungeonSpec.EntryMode.PERIMETER
                || (env.surface()!=IslandPlan.Surface.GRASS && env.surface()!=IslandPlan.Surface.ROCK))
            throw new IllegalArgumentException("Tree house requires a dry perimeter");
        if (pieces.size()!=13 || pieces.stream().anyMatch(WdaTemplatePiece::terrainMatching))
            throw new IllegalArgumentException("Tree house requires both rigid sections and all eleven encounters");
        var root=pieces.get(0);
        if ((!root.id().equals(PREFIX+"0_main") && !root.id().equals(PREFIX+"1_roots"))
                || root.origin().getY()!=env.firstFreeY()-6)
            throw new IllegalArgumentException("Unexpected original tree house root");
        var body=pieces.stream().filter(p->p.id().equals(PREFIX+"0_main")).findFirst().orElseThrow(()->new IllegalArgumentException("Missing tree house body"));
        var origin=body.origin();
        var rotation=root.rotation();
        var expectedBody=root.id().equals(PREFIX+"1_roots") ? root.origin().above(16) : root.origin();
        body.requireFrame(expectedBody,rotation,31,48,31);
        var byOrigin=new HashMap<BlockPos,WdaTemplatePiece>();
        for (var p:pieces) if (byOrigin.put(p.origin(),p)!=null) throw new IllegalArgumentException("Duplicate tree house piece");
        var roots=byOrigin.remove(origin.below(16));
        if (roots==null || !roots.id().equals(PREFIX+"1_roots")) throw new IllegalArgumentException("Missing original tree roots");
        roots.requireFrame(origin.below(16),rotation,31,16,31);
        byOrigin.remove(origin);
        var inhabitants=new ArrayList<Spawn>();
        for (var socket:SOCKETS) {
            var pos=socket.pos().above().rotate(rotation).offset(origin);
            var child=byOrigin.remove(pos);
            if (child==null || (!child.id().equals(PREFIX+"husk_0") && !child.id().equals(PREFIX+"skeleton_0")))
                throw new IllegalArgumentException("Missing tree house encounter at "+pos);
            child.requireFrame(pos,rotation.getRotated(socket.rotation()),1,3,1);
            inhabitants.add(new Spawn(pos.above(),child.id().equals(PREFIX+"husk_0")?"minecraft:husk":"minecraft:skeleton"));
        }
        return new TreeHouseLayout(origin,rotation,List.copyOf(inhabitants));
    }

    boolean matchesEntities(List<Spawn> actual) {
        return actual.size()==inhabitants.size() && new HashSet<>(actual).equals(new HashSet<>(inhabitants));
    }
    int groundY() { return Math.addExact(origin.getY(),6); }
    BlockPos pos(BlockPos local) { return local.rotate(rotation).offset(origin); }
    Cell cell(BlockPos local) {
        var p=pos(local);
        return new Cell(p.getX(),p.getY(),p.getZ());
    }
    @Override public TreeHouseLayout move(int x,int y,int z) {
        return new TreeHouseLayout(origin.offset(x,y,z),rotation,
                inhabitants.stream().map(s->new Spawn(s.position().offset(x,y,z),s.type())).toList());
    }
    @Override public BooleanSupplier survey(ServerLevel world,IslandPlan island,int exits) {
        return TreeHouseRoute.survey(this,island,exits,new TreeHouseProbe(world));
    }
}
