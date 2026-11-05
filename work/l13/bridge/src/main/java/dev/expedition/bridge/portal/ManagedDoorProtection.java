package dev.expedition.bridge.portal;

import dev.expedition.bridge.domain.PortalRecord;
import dev.expedition.bridge.storage.ExpeditionSavedData;
import dev.expedition.bridge.content.PocketIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.dimdev.dimdoors.block.entity.EntranceRiftBlockEntity;
import org.dimdev.dimdoors.block.entity.RiftBlockEntity;
import org.dimdev.dimdoors.world.ModDimensions;
import org.dimdev.dimdoors.world.level.registry.DimensionalRegistry;

/** Both halves and their support stay intact until the tracked visit is closed. */
public final class ManagedDoorProtection {
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void breakBlock(BlockEvent.BreakEvent event) {
        if(event.getLevel() instanceof ServerLevel level && protectedAt(level,event.getPos())) event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void explosion(ExplosionEvent.Detonate event) {
        if(event.getLevel() instanceof ServerLevel level)
            event.getAffectedBlocks().removeIf(pos->protectedAt(level,pos));
    }
    private boolean protectedAt(ServerLevel level, BlockPos pos) {
        var data=ExpeditionSavedData.get(level.getServer());
        for(int dy=-1;dy<=1;dy++) {
            var r=data.at(new PortalRecord.Site(level.dimension().location().toString(),pos.getX(),pos.getY()+dy,pos.getZ()));
            if(r!=null && r.phase()!=PortalRecord.Phase.CLOSED
                    && level.getBlockEntity(pos.offset(0,dy,0)) instanceof EntranceRiftBlockEntity rift
                    && rift.getDestination() instanceof ManagedEntranceTarget target
                    && r.id().equals(target.portalId()))return true;
            var candidate=new PortalRecord.Site(level.dimension().location().toString(),pos.getX(),pos.getY()+dy,pos.getZ());
            if(level.getBlockEntity(pos.offset(0,dy,0)) instanceof RiftBlockEntity exit
                    && exit.getDestination() instanceof ManagedExitTarget target) {
                var owner=data.get(target.portalId());
                if(owner!=null && owner.exits().contains(candidate))return true;
            }
            if(level.dimension().equals(ModDimensions.DUNGEON)) {
                var pocket=DimensionalRegistry.getPocketDirectory(ModDimensions.DUNGEON).getPocketAt(pos.offset(0,dy,0));
                if(pocket!=null && pocket.hasAddon(PocketIdentity.ID)) {
                    PocketIdentity identity=pocket.getAddon(PocketIdentity.ID);var owner=data.get(identity.portalId());
                    if(owner!=null && owner.isInternalDevice(candidate))return true;
                }
            }
        }
        return false;
    }
}
