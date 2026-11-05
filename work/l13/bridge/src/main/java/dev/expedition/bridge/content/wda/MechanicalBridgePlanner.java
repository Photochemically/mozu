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
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import java.util.*;

/** Uses the selected native graph's actual transformed joints, never an independently rolled bridge. */
final class MechanicalBridgePlanner {
    static MechanicalBridgeLayout prepare(MinecraftServer server,StructureStart start) {
        var pieces=new ArrayList<MechanicalBridgeLayout.Piece>();
        for(var piece:start.getPieces()) {
            var pool=(PoolElementStructurePiece)piece;
            var encoded=StructurePoolElement.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE,server.registryAccess()),pool.getElement())
                    .getOrThrow(false,message->{throw new IllegalArgumentException(message);});
            if(!(encoded instanceof CompoundTag tag) || !tag.contains("location",Tag.TAG_STRING))throw new IllegalArgumentException("Unknown bridge template");
            var joints=new ArrayList<MechanicalBridgeLayout.Joint>();
            for(var joint:pool.getElement().getShuffledJigsawBlocks(server.getStructureManager(),pool.getPosition(),pool.getRotation(),RandomSource.create(0))) {
                var front=JigsawBlock.getFrontFacing(joint.state());
                if(joint.nbt()==null || !front.getAxis().isHorizontal() || JigsawBlock.getTopFacing(joint.state())!=Direction.UP)continue;
                var p=joint.pos();var n=joint.nbt();
                joints.add(new MechanicalBridgeLayout.Joint(new Cell(p.getX(),p.getY(),p.getZ()),front.getStepX(),front.getStepZ(),
                        n.getString("name"),n.getString("target"),n.getString("pool"),n.getString("final_state")));
            }
            pieces.add(new MechanicalBridgeLayout.Piece(tag.getString("location"),WdaStructurePlan.bounds(piece.getBoundingBox()),joints));
        }
        return MechanicalBridgeLayout.fromPieces(pieces);
    }
}
