package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import java.util.*;

final class ScorchedWellPlanner {
    static ScorchedWellLayout prepare(MinecraftServer server,StructureStart start) {
        var pieces=new ArrayList<ScorchedWellLayout.Piece>();
        for(var raw:start.getPieces()) {
            var pool=(PoolElementStructurePiece)raw;
            var encoded=StructurePoolElement.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE,server.registryAccess()),pool.getElement())
                    .getOrThrow(false,message->{throw new IllegalArgumentException(message);});
            if(!(encoded instanceof CompoundTag tag) || !tag.contains("location",Tag.TAG_STRING) || !tag.getString("processors").equals("minecraft:empty")
                    || pool.getElement().getProjection()!=StructureTemplatePool.Projection.RIGID
                    || !server.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST).get(ResourceLocation.parse("minecraft:empty")).list().isEmpty())
                throw new IllegalArgumentException("Lava well requires original rigid pieces and empty processors");
            var template=server.getStructureManager().get(ResourceLocation.parse(tag.getString("location"))).orElseThrow();
            var lava=template.filterBlocks(pool.getPosition(),new StructurePlaceSettings().setRotation(pool.getRotation()),Blocks.LAVA,true)
                    .stream().map(b->new Cell(b.pos().getX(),b.pos().getY(),b.pos().getZ())).toList();
            var p=pool.getPosition();int r=switch(pool.getRotation()){case NONE->0;case CLOCKWISE_90->1;case CLOCKWISE_180->2;case COUNTERCLOCKWISE_90->3;};
            var piece=new ScorchedWellLayout.Piece(tag.getString("location"),new Cell(p.getX(),p.getY(),p.getZ()),r,WdaStructurePlan.bounds(raw.getBoundingBox()),lava);pieces.add(piece);
            var expected=ScorchedWellJoints.forTemplate(piece.template());
            if(expected.isEmpty())continue;
            var joints=pool.getElement().getShuffledJigsawBlocks(server.getStructureManager(),p,pool.getRotation(),RandomSource.create(0));
            for(var e:expected) {
                var q=piece.at(e.x(),e.y(),e.z());var front=pool.getRotation().rotate(Objects.requireNonNull(Direction.byName(e.front())));
                var top=pool.getRotation().rotate(Objects.requireNonNull(Direction.byName(e.top())));
                long matches=joints.stream().filter(j->j.pos().getX()==q.x()&&j.pos().getY()==q.y()&&j.pos().getZ()==q.z()
                        && JigsawBlock.getFrontFacing(j.state())==front && JigsawBlock.getTopFacing(j.state())==top && j.nbt()!=null
                        && j.nbt().getString("name").equals(e.name()) && j.nbt().getString("target").equals(e.target())
                        && j.nbt().getString("pool").equals(e.pool()) && j.nbt().getString("joint").equals(e.alignment())
                        && j.nbt().getString("final_state").equals("minecraft:air")).count();
                if(matches!=1)throw new IllegalArgumentException("Original well/hub joint changed or missing");
            }
        }
        return ScorchedWellLayout.fromPieces(pieces);
    }
}
