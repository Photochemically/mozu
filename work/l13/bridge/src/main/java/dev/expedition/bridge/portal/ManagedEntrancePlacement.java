package dev.expedition.bridge.portal;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.storage.*;
import dev.expedition.bridge.worldgen.NaturalEntranceSite;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.dimdev.dimdoors.block.entity.EntranceRiftBlockEntity;
import java.io.IOException;

/** Shared block placement for natural and gated diagnostic entrances. Never generates a pocket. */
public final class ManagedEntrancePlacement {
    private ManagedEntrancePlacement(){}
    public static PortalRecord place(ServerLevel level,BlockPos pos,NaturalEntryPlan.Type type,int facing,PortalRecord portal) throws IOException {
        if(!level.getServer().isSameThread() || !level.dimension().equals(Level.OVERWORLD) || facing<0 || facing>3
                || portal.phase()!=PortalRecord.Phase.NEW || portal.destination()!=null
                || portal.kind()!=type.kind || portal.tier()!=type.tier
                || !portal.entrance().equals(new PortalRecord.Site("minecraft:overworld",pos.getX(),pos.getY(),pos.getZ()))
                || !NaturalEntranceSite.safe(level,pos))throw new IllegalArgumentException("Entrance requires a new record and safe loaded overworld terrain");
        var block=BuiltInRegistries.BLOCK.get(ResourceLocation.parse(type.block));
        if(!(block instanceof org.dimdev.dimdoors.block.door.DimensionalDoorBlock))throw new IllegalStateException("Required native door unavailable: "+type.block);
        var data=ExpeditionSavedData.get(level.getServer());data.add(portal);
        Direction[] directions={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
        var lower=block.defaultBlockState().setValue(DoorBlock.FACING,directions[facing]).setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER);
        try {
            // Selected diagnostic content is included in this first checkpoint, before either door half exists.
            PortalCheckpoint.write(level.getServer(),data);
            if(!level.setBlock(pos,lower,18) || !(level.getBlockEntity(pos) instanceof EntranceRiftBlockEntity rift))
                throw new IllegalStateException("Entrance placement failed");
            rift.setDestination(new ManagedEntranceTarget(portal.id()));rift.register();rift.setChanged();
            if(!level.setBlock(pos.above(),lower.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),18))
                throw new IllegalStateException("Entrance upper half placement failed");
            level.blockUpdated(pos,block);level.blockUpdated(pos.above(),block);return portal;
        } catch(IOException|RuntimeException failure) {
            portal.quarantine(failure.toString());data.setDirty();
            try{PortalCheckpoint.write(level.getServer(),data);}catch(IOException save){failure.addSuppressed(save);}
            throw failure;
        }
    }
}
