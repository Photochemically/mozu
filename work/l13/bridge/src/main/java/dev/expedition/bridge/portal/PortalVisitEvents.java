package dev.expedition.bridge.portal;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.storage.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.dimdev.dimdoors.block.entity.EntranceRiftBlockEntity;
import org.dimdev.dimdoors.world.ModDimensions;

/** Observes actual positions. Living spaces and Limbo retain the original participant until a safe departure. */
public final class PortalVisitEvents {
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        var server=ServerLifecycleHooks.getCurrentServer();
        if(event.phase!=TickEvent.Phase.END || server==null || server.getTickCount()%20!=0)return;
        var data=ExpeditionSavedData.get(server);
        for(var portal:data.records()) {
            if(portal.phase()==PortalRecord.Phase.CLOSING) { removeLoadedEntrance(server,data,portal);continue; }
            boolean limboOnly=portal.phase()==PortalRecord.Phase.NEW && portal.visits().enteredBefore();
            if(!limboOnly && (portal.phase()!=PortalRecord.Phase.READY || !GoldPortalService.nativeIdentityMatches(server,portal)))continue;
            // Discover visitors arriving through a different existing link before evaluating closure.
            for(var player:server.getPlayerList().getPlayers()) {
                if(!player.isAlive())continue;
                var before=portal.visits().snapshot().get(player.getUUID());
                if(GoldPortalService.inside(portal,player)) {
                    if(portal.returnAnchor(player.getUUID())==null){portal.bindReturnAnchor(player.getUUID(),portal.entrance());data.setDirty();}
                    if(portal.livingVisitors().contains(player.getUUID())){portal.livingVisit(player.getUUID(),false);data.setDirty();}
                    if(before!=VisitLedger.Presence.ACTIVE) { portal.entered(player.getUUID());data.setDirty(); }
                } else if(before!=null && before!=VisitLedger.Presence.EXITED && outsideExpedition(player)) {
                    if(before==VisitLedger.Presence.ENTERING && FirstVisitService.nearEntrance(player,portal))continue;
                    if(portal.returnAnchor(player.getUUID())!=null)portal.livingVisit(player.getUUID(),false);
                    portal.visits().confirmedSafeExit(player.getUUID());data.setDirty();
                }
            }
            if(portal.beginClosingIfEmpty()) {
                data.setDirty();
                removeLoadedEntrance(server,data,portal);
            }
        }
    }
    private boolean outsideExpedition(ServerPlayer player) {
        return !ModDimensions.isPocketDimension(player.level().dimension()) && !player.level().dimension().equals(ModDimensions.LIMBO)
                && SafeLanding.safe(player,player.serverLevel(),player.blockPosition());
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) {
            var data=ExpeditionSavedData.get(player.server);
            for(var r:data.records())if(r.visits().hasParticipant(player.getUUID())) {
                r.visits().disconnected(player.getUUID());data.setDirty();
            }
        }
    }
    @SubscribeEvent public void death(LivingDeathEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) {
            var data=ExpeditionSavedData.get(player.server);
            for(var r:data.records())if(r.visits().hasParticipant(player.getUUID())) {
                r.visits().died(player.getUUID());data.setDirty();
            }
        }
    }
    private void removeLoadedEntrance(MinecraftServer server, ExpeditionSavedData data, PortalRecord portal) {
        var site=portal.entrance();var pos=new BlockPos(site.x(),site.y(),site.z());
        var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(site.dimension())));
        if(level==null || !level.hasChunkAt(pos))return; // No force-loading an abandoned entrance.
        if(!(level.getBlockEntity(pos) instanceof EntranceRiftBlockEntity rift)
                || !(rift.getDestination() instanceof ManagedEntranceTarget target) || !portal.id().equals(target.portalId()))return;
        if(!(level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.DoorBlock))return;
        if(!level.getBlockState(pos.above()).is(level.getBlockState(pos).getBlock()))return;
        // Also checkpoint on resumed CLOSING records; failed writes must never be bypassed on the next tick.
        try { PortalCheckpoint.write(server,data); }
        catch(java.io.IOException failure) { dev.expedition.bridge.ExpeditionBridge.LOGGER.error("Cannot checkpoint special entrance closure {}",portal.id(),failure);return; }
        rift.unregister();
        level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        if(level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above())) { portal.entranceRemoved();data.setDirty(); }
    }
}
