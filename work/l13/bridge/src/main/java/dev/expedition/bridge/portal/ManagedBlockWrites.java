package dev.expedition.bridge.portal;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.storage.ExpeditionSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.dimdev.dimdoors.world.ModDimensions;

/** Block identity protection. Property updates (opening doors, redstone) remain normal. */
public final class ManagedBlockWrites {
    private ManagedBlockWrites() {}

    public static boolean denyReplacement(ServerLevel level,BlockPos pos,BlockState before,BlockState after) {
        // Consume before callbacks, even if this write would not otherwise require an exemption.
        if(ExactBlockWrite.consume(level,pos,before,after))return false;
        if(before.getBlock()==after.getBlock())return false;
        return protectedPosition(level,pos);
    }

    public static boolean protectedPosition(ServerLevel level,BlockPos pos) {
        // Background world-generation writes are not live device operations. Do not access SavedData off-thread.
        if(!level.getServer().isSameThread() || level.getServer().overworld()==null)return false;
        if(!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                && !level.dimension().equals(ModDimensions.DUNGEON))return false;
        return ManagedDoorProtection.recordedAt(level,pos);
    }

    public static boolean setEntranceBlock(ServerLevel level,PortalRecord portal,BlockPos pos,BlockState after,int flags) {
        var data=ExpeditionSavedData.get(level.getServer());
        var before=level.getBlockState(pos);
        var site=new PortalRecord.Site(level.dimension().location().toString(),pos.getX(),pos.getY(),pos.getZ());
        if(data.get(portal.id())!=portal || !EntranceWritePolicy.allows(portal,site,
                BuiltInRegistries.BLOCK.getKey(before.getBlock()).toString(),BuiltInRegistries.BLOCK.getKey(after.getBlock()).toString()))
            throw new IllegalStateException("Unapproved managed entrance write");
        return ExactBlockWrite.run(level,pos,before,after,()->level.setBlock(pos,after,flags));
    }
}
