package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.DungeonSpec;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import java.util.*;
import java.util.function.BooleanSupplier;

record GiantMushroomLayout(Form form, BlockPos origin, Rotation rotation) implements PerimeterLayout {
    static final String TARGET = "dungeons_arise:giant_mushroom";
    private static final String DIRECTORY = TARGET + "/";

    enum Form {
        RED("red", 21, 21, 38, 4,
                List.of(new BlockPos(32,16,24)), List.of(new BlockPos(35,8,22))),
        TWIN("twin", 9, 19, 32, 8,
                List.of(new BlockPos(20,20,28), new BlockPos(20,47,30)),
                List.of(new BlockPos(18,21,32), new BlockPos(20,50,30)));

        final String name;
        final int eastWidth, southDepth, layerHeight, parts;
        final List<BlockPos> chests, spawners;

        Form(String name,int eastWidth,int southDepth,int layerHeight,int parts,List<BlockPos> chests,List<BlockPos> spawners) {
            this.name=name; this.eastWidth=eastWidth; this.southDepth=southDepth; this.layerHeight=layerHeight;
            this.parts=parts; this.chests=chests; this.spawners=spawners;
        }
        String part(int number) { return DIRECTORY + name + "_giant_mushroom_part_" + number; }
        String encounter() { return DIRECTORY + "giant_mushroom_skeleton_0_" + (this==TWIN ? "twins" : "red"); }
        String loot() { return "dungeons_arise:chests/giant_mushroom/" + name + "_giant_mushroom"; }
    }

    static GiantMushroomLayout inspect(List<WdaTemplatePiece> pieces,WdaStructurePlanner.Environment env) {
        if (env.boarding() || env.entryMode()!=DungeonSpec.EntryMode.PERIMETER
                || (env.surface()!=IslandPlan.Surface.GRASS && env.surface()!=IslandPlan.Surface.ROCK))
            throw new IllegalArgumentException("Giant mushroom requires a dry perimeter");
        if (pieces.isEmpty()) throw new IllegalArgumentException("Missing giant mushroom root");
        var root=pieces.get(0);
        var form=Arrays.stream(Form.values()).filter(f->f.part(0).equals(root.id())).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Unexpected giant mushroom root"));
        if (root.origin().getY()!=env.firstFreeY()-6 || pieces.size()!=form.parts+form.spawners.size())
            throw new IllegalArgumentException("Incomplete giant mushroom or unexpected surface height");
        var byOrigin=new HashMap<BlockPos,WdaTemplatePiece>();
        for (var piece:pieces) if (piece.terrainMatching() || byOrigin.put(piece.origin(),piece)!=null)
            throw new IllegalArgumentException("Duplicate or projected giant mushroom piece");
        var layout=new GiantMushroomLayout(form,root.origin(),root.rotation());
        // Each layer is a ring of four quadrants, numbered clockwise from the northwest.
        for (int i=0;i<form.parts;i++) {
            int quadrant=i%4;
            boolean east=quadrant==1 || quadrant==2, south=quadrant>=2;
            var local=new BlockPos(east?32:0, i>=4?32:0, south?32:0);
            require(byOrigin,form.part(i),layout.position(local),root.rotation(),
                    east?form.eastWidth:32,form.layerHeight,south?form.southDepth:32);
        }
        // The down-west spawner inlet must align with its up-north parent.
        for (var socket:form.spawners) require(byOrigin,form.encounter(),layout.position(socket),
                root.rotation().getRotated(Rotation.CLOCKWISE_90),1,2,1);
        return layout;
    }

    private static void require(Map<BlockPos,WdaTemplatePiece> pieces,String id,BlockPos origin,Rotation rotation,int w,int h,int d) {
        var piece=pieces.get(origin);
        if (piece==null || !piece.id().equals(id)) throw new IllegalArgumentException("Missing original piece "+id+" at "+origin);
        piece.requireFrame(origin,rotation,w,h,d);
    }

    BlockPos position(BlockPos local) { return local.rotate(rotation).offset(origin); }
    Cell cell(BlockPos local) {
        var p=position(local);
        return new Cell(p.getX(),p.getY(),p.getZ());
    }
    @Override public GiantMushroomLayout move(int x,int y,int z) { return new GiantMushroomLayout(form,origin.offset(x,y,z),rotation); }
    @Override public BooleanSupplier survey(ServerLevel world,IslandPlan island,int exits) {
        return GiantMushroomRoute.survey(this,island,exits,new GiantMushroomProbe(world));
    }
}
