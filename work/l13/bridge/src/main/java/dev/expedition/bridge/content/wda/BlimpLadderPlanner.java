package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.*;
import java.util.*;

/** Validates actual transformed native joints and processor/projection contracts before allocating terrain. */
final class BlimpLadderPlanner {
    static BlimpLadderLayout prepare(MinecraftServer server,StructureStart start,BlockBounds complete) {
        var pieces=new ArrayList<BlimpLadderLayout.Piece>();
        for(var raw:start.getPieces()) {
            var pool=(PoolElementStructurePiece)raw;
            var encoded=StructurePoolElement.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE,server.registryAccess()),pool.getElement())
                    .getOrThrow(false,message->{throw new IllegalArgumentException(message);});
            if(!(encoded instanceof CompoundTag tag) || !tag.contains("location",Tag.TAG_STRING)
                    || !tag.getString("processors").equals("minecraft:empty")
                    || pool.getElement().getProjection()!=StructureTemplatePool.Projection.RIGID)
                throw new IllegalArgumentException("Blimp requires audited rigid original pieces without custom processors");
            var pos=pool.getPosition();int rotation=switch(pool.getRotation()) {
                case NONE->0;case CLOCKWISE_90->1;case CLOCKWISE_180->2;case COUNTERCLOCKWISE_90->3;
            };
            var piece=new BlimpLadderLayout.Piece(tag.getString("location"),new Cell(pos.getX(),pos.getY(),pos.getZ()),rotation);
            pieces.add(piece);
            var joints=pool.getElement().getShuffledJigsawBlocks(server.getStructureManager(),pos,pool.getRotation(),RandomSource.create(0));
            var expected=new ArrayList<Joint>();
            if(piece.template().equals(BlimpLadderLayout.LADDER)) {
                expected.add(new Joint(1,0,0,Direction.DOWN,Direction.NORTH,"ladders","ladders"));
                expected.add(new Joint(1,47,0,Direction.UP,Direction.NORTH,"ladders","ladders"));
            } else if(piece.template().equals(BlimpLadderLayout.HULL0)) {
                expected.add(new Joint(28,0,11,Direction.DOWN,Direction.NORTH,"ladders","ladders"));
                expected.add(new Joint(30,0,0,Direction.EAST,Direction.UP,"blimp_side_1","blimp_side_0"));
            } else if(piece.template().equals(BlimpLadderLayout.HULL1))
                expected.add(new Joint(0,0,0,Direction.WEST,Direction.UP,"blimp_side_0","blimp_side_1"));
            for(var e:expected) {
                var p=piece.at(e.x,e.y,e.z);
                long matches=joints.stream().filter(j->j.pos().getX()==p.x() && j.pos().getY()==p.y() && j.pos().getZ()==p.z()
                        && JigsawBlock.getFrontFacing(j.state())==pool.getRotation().rotate(e.front)
                        && JigsawBlock.getTopFacing(j.state())==pool.getRotation().rotate(e.top)
                        && j.nbt()!=null && j.nbt().getString("name").equals("dungeons_arise:"+e.name)
                        && j.nbt().getString("target").equals("dungeons_arise:"+e.target)
                        && j.nbt().getString("pool").equals("dungeons_arise:enigmatic/small_blimp/small_blimp_main")
                        && j.nbt().getString("final_state").equals("minecraft:air") && j.nbt().getString("joint").equals("aligned")).count();
                if(matches!=1)throw new IllegalArgumentException("Original blimp joint changed or missing: "+piece.template());
            }
            if(!expected.isEmpty()) {
                int sx=piece.template().equals(BlimpLadderLayout.LADDER)?2:31,sy=sx==2?48:25,sz=sx==2?1:31;
                var a=piece.at(0,0,0);var b=piece.at(sx-1,sy-1,sz-1);
                var bounds=new BlockBounds(Math.min(a.x(),b.x()),a.y(),Math.min(a.z(),b.z()),Math.max(a.x(),b.x()),b.y(),Math.max(a.z(),b.z()));
                if(!bounds.equals(WdaStructurePlan.bounds(raw.getBoundingBox())))throw new IllegalArgumentException("Original blimp dimensions changed");
            }
        }
        return BlimpLadderLayout.fromPieces(pieces,complete);
    }
    private record Joint(int x,int y,int z,Direction front,Direction top,String name,String target){}
}
