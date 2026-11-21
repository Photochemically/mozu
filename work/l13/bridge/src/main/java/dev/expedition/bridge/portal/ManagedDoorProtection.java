package dev.expedition.bridge.portal;

import dev.expedition.bridge.domain.PortalRecord;
import dev.expedition.bridge.storage.ExpeditionSavedData;
import dev.expedition.bridge.content.PocketIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import java.util.List;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.dimdev.dimdoors.block.entity.RiftBlockEntity;
import org.dimdev.dimdoors.world.ModDimensions;
import org.dimdev.dimdoors.world.level.registry.DimensionalRegistry;

/** Exact managed device positions; ordinary construction and interaction are not blanket-disabled. */
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
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level))return;
        if(event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            if(multi.getReplacedBlockSnapshots().stream().anyMatch(s->protectedAt(level,s.getPos())))event.setCanceled(true);
        } else if(protectedAt(level,event.getPos()))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void piston(PistonEvent.Pre event) {
        if(!(event.getLevel() instanceof ServerLevel level))return;
        boolean extending=event.getPistonMoveType().isExtend;
        List<BlockPos> moved=List.of(),destroyed=List.of();
        // Ordinary retracting pistons do not pull blocks. A failed resolver still removes the head.
        if(extending || event.getState().is(Blocks.STICKY_PISTON)) {
            var helper=event.getStructureHelper();
            if(helper!=null && helper.resolve()){moved=helper.getToPush();destroyed=helper.getToDestroy();}
        }
        // Retraction can finish a moving block two cells ahead without including it in the resolver lists.
        var finish=event.getPos().relative(event.getDirection(),2);
        boolean finishesMoving=!extending && event.getState().is(Blocks.STICKY_PISTON)
                && level.getBlockState(finish).is(Blocks.MOVING_PISTON)
                && level.getBlockEntity(finish) instanceof PistonMovingBlockEntity moving
                && moving.getDirection()==event.getDirection() && moving.isExtending();
        if(BlockMutationFootprint.piston(event.getPos(),event.getDirection(),extending,moved,destroyed,
                    finishesMoving?List.of(finish):List.of())
                .stream().anyMatch(pos->protectedAt(level,pos)))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void toolChange(BlockEvent.BlockToolModificationEvent event) {
        if(event.getLevel() instanceof ServerLevel level && protectedAt(level,event.getPos()))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void fluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if(event.getLevel() instanceof ServerLevel level && protectedAt(level,event.getPos()))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void mobDestroy(LivingDestroyBlockEvent event) {
        if(event.getEntity().level() instanceof ServerLevel level && protectedAt(level,event.getPos()))event.setCanceled(true);
    }
    public static boolean protectedAt(ServerLevel level, BlockPos pos) {
        if(recordedAt(level,pos))return true;
        // Event-only fallback for an existing rift whose native pocket identity needs repair.
        // Never use this lookup in a chunk write hook: reading entities there could generate other chunks.
        var data=ExpeditionSavedData.get(level.getServer());
        if(data.records().isEmpty() || !level.hasChunkAt(pos))return false;
        for(int dy=-1;dy<=1;dy++) {
            var candidate=pos.offset(0,dy,0);
            if(level.getBlockEntity(candidate) instanceof RiftBlockEntity exit
                    && exit.getDestination() instanceof ManagedExitTarget target) {
                var owner=data.get(target.portalId());
                if(owner!=null && owner.exits().contains(site(level,candidate)))return true;
            }
        }
        return false;
    }
    /** Saved coordinates and the in-memory native directory only; no world or block-entity reads. */
    public static boolean recordedAt(ServerLevel level,BlockPos pos) {
        var data=ExpeditionSavedData.get(level.getServer());
        if(data.records().isEmpty())return false;
        PortalRecord owner=null;
        if(level.dimension().equals(ModDimensions.DUNGEON)) {
            var pocket=DimensionalRegistry.getPocketDirectory(ModDimensions.DUNGEON).getPocketAt(pos);
            if(pocket!=null && pocket.hasAddon(PocketIdentity.ID)) {
                PocketIdentity identity=pocket.getAddon(PocketIdentity.ID);owner=data.get(identity.portalId());
                if(owner!=null && owner.isProtectedBlock(site(level,pos)))return true;
            }
        }
        for(int dy=-1;dy<=1;dy++) {
            var r=data.at(new PortalRecord.Site(level.dimension().location().toString(),pos.getX(),pos.getY()+dy,pos.getZ()));
            // Placement events may be posted after the original block entity has been replaced.
            if(r!=null && r.phase()!=PortalRecord.Phase.CLOSED)return true;
            var candidate=new PortalRecord.Site(level.dimension().location().toString(),pos.getX(),pos.getY()+dy,pos.getZ());
            if(owner!=null && (owner.isInternalDevice(candidate)||owner.exits().contains(candidate)))return true;
        }
        return false;
    }
    private static PortalRecord.Site site(ServerLevel level,BlockPos pos) {
        return new PortalRecord.Site(level.dimension().location().toString(),pos.getX(),pos.getY(),pos.getZ());
    }
}
