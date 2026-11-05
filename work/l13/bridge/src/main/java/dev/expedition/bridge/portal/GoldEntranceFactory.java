package dev.expedition.bridge.portal;

import dev.expedition.bridge.domain.PortalKind;
import dev.expedition.bridge.domain.PortalRecord;
import dev.expedition.bridge.storage.ExpeditionSavedData;
import dev.expedition.bridge.storage.PortalCheckpoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.dimdev.dimdoors.block.entity.EntranceRiftBlockEntity;
import java.io.IOException;
import java.util.UUID;

/** Worldgen integration calls this on the server thread, after checking its configured natural spawn rule. */
public final class GoldEntranceFactory {
    public static final ResourceLocation BLOCK_ID=ResourceLocation.fromNamespaceAndPath("dimdoors", "block_ag_dim_dimdoors_gold_door");
    private GoldEntranceFactory() {}
    public static PortalRecord place(ServerLevel level, BlockPos pos, Direction facing, long seed) throws IOException {
        if(!level.getServer().isSameThread() || !level.dimension().equals(Level.OVERWORLD))
            throw new IllegalArgumentException("Natural reward entrances require the overworld server thread");
        if(facing.getAxis()==Direction.Axis.Y || pos.getY()<=level.getMinBuildHeight() || pos.getY()+1>=level.getMaxBuildHeight()
                || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()
                || !level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP))
            throw new IllegalArgumentException("Reward door requires a loaded, clear and supported two-block site");
        Block door=BuiltInRegistries.BLOCK.get(BLOCK_ID);
        if(!(door instanceof DoorBlock))throw new IllegalStateException("Native gold dimensional door is not registered");
        var data=ExpeditionSavedData.get(level.getServer());
        var portal=new PortalRecord(UUID.randomUUID(),PortalKind.GOLD,0,new PortalRecord.Site(level.dimension().location().toString(),pos.getX(),pos.getY(),pos.getZ()),seed);
        data.add(portal);PortalCheckpoint.write(level.getServer(),data);
        var lower=door.defaultBlockState().setValue(DoorBlock.FACING,facing).setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER);
        var upper=lower.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER);
        try {
            if(!level.setBlock(pos,lower,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE)
                    || !level.setBlock(pos.above(),upper,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE))
                throw new IllegalStateException("Reward door placement failed");
            if(!(level.getBlockEntity(pos) instanceof EntranceRiftBlockEntity rift))
                throw new IllegalStateException("Reward door has no native rift block entity");
            rift.setDestination(new ManagedEntranceTarget(portal.id()));rift.register();rift.setChanged();
            level.blockUpdated(pos,door);level.blockUpdated(pos.above(),door);
            return portal;
        } catch(RuntimeException failure) {
            portal.generationFailed(failure.toString());data.setDirty();
            throw failure;
        }
    }
}
