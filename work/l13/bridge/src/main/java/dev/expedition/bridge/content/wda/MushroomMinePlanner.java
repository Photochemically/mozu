package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.*;
import java.util.*;

/** Strictly audits the native graph; does not synthesize missing cubes or rewrite original templates. */
final class MushroomMinePlanner {
    static MushroomMineLayout prepare(MinecraftServer server,StructureStart start,BlockBounds complete) {
        var pieces=new ArrayList<MushroomMineLayout.Piece>();
        for(var raw:start.getPieces()) {
            var pool=(PoolElementStructurePiece)raw;
            var encoded=StructurePoolElement.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE,server.registryAccess()),pool.getElement())
                    .getOrThrow(false,message->{throw new IllegalArgumentException(message);});
            if(!(encoded instanceof CompoundTag tag) || !tag.contains("location",Tag.TAG_STRING)
                    || !tag.getString("processors").equals("minecraft:empty") || pool.getElement().getProjection()!=StructureTemplatePool.Projection.RIGID
                    || !server.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST).get(ResourceLocation.parse("minecraft:empty")).list().isEmpty())
                throw new IllegalArgumentException("Sparse mine requires original rigid pieces and an empty processor list");
            var pos=pool.getPosition();int rotation=switch(pool.getRotation()){case NONE->0;case CLOCKWISE_90->1;case CLOCKWISE_180->2;case COUNTERCLOCKWISE_90->3;};
            var piece=new MushroomMineLayout.Piece(tag.getString("location"),new Cell(pos.getX(),pos.getY(),pos.getZ()),rotation);pieces.add(piece);
            if(piece.part()<0)continue;
            if(!piece.bounds().equals(WdaStructurePlan.bounds(raw.getBoundingBox())))throw new IllegalArgumentException("Original mine cube dimensions changed");
            var joints=pool.getElement().getShuffledJigsawBlocks(server.getStructureManager(),pos,pool.getRotation(),RandomSource.create(0));
            for(var e:MushroomMineLayout.joints(piece.part())) {
                var p=piece.at(e.x(),e.y(),e.z());var directions=e.orientation().split("_");
                var front=pool.getRotation().rotate(Objects.requireNonNull(Direction.byName(directions[0])));
                var top=pool.getRotation().rotate(Objects.requireNonNull(Direction.byName(directions[1])));
                long matches=joints.stream().filter(j->j.pos().getX()==p.x() && j.pos().getY()==p.y() && j.pos().getZ()==p.z()
                        && JigsawBlock.getFrontFacing(j.state())==front && JigsawBlock.getTopFacing(j.state())==top && j.nbt()!=null
                        && j.nbt().getString("name").equals("dungeons_arise:mushroom_mines_"+e.name())
                        && j.nbt().getString("target").equals("dungeons_arise:mushroom_mines_"+e.target())
                        && j.nbt().getString("pool").equals("dungeons_arise:fungi/mushroom_mines/mushroom_mines_"+e.pool())
                        && j.nbt().getString("final_state").equals("minecraft:structure_void") && j.nbt().getString("joint").equals(e.alignment())).count();
                if(matches!=1)throw new IllegalArgumentException("Original mine joint changed or missing");
            }
        }
        return MushroomMineLayout.fromPieces(pieces,complete);
    }
}
