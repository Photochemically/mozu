package dev.expedition.bridge.portal;

import dev.expedition.bridge.ExpeditionBridge;
import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.storage.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.dimdev.dimdoors.world.ModDimensions;

public final class TravelEvents {
    public static final String PLAYER_MARKER="expedition_bridge_transit";
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var server=event.getServer();var data=ExpeditionSavedData.get(server);
        for(var player:server.getPlayerList().getPlayers()) {
            if(!player.isAlive())continue;
            // Pending operations from other failed doors remain journaled; they never reroll or block unrelated doors.
            for(var op:data.travel().operations()) {
                if(!op.player().equals(player.getUUID()) || op.state()!=TravelOperation.State.PREPARED
                        || !player.getPersistentData().hasUUID(PLAYER_MARKER)
                        || !player.getPersistentData().getUUID(PLAYER_MARKER).equals(op.id()) || !ManagedTransit.atLanding(player,op))continue;
                var portal=data.get(op.portal());
                if(portal==null || op.route().living())continue;
                try { ManagedTransit.finish(player,portal,op);PortalCheckpoint.write(server,data); }
                catch(Exception failure){ExpeditionBridge.LOGGER.error("Cannot reconcile transit {}",op.id(),failure);}
            }
            if(data.travel().releaseIfClear(player.getUUID(),SafeLanding.site(player.serverLevel(),player.blockPosition()),server.overworld().getGameTime()))data.setDirty();
        }
    }
    @SubscribeEvent public void dimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        var data=ExpeditionSavedData.get(player.server);boolean changed=false;
        boolean living=player.level().dimension().equals(ModDimensions.PUBLIC) || player.level().dimension().equals(ModDimensions.PERSONAL);
        for(var portal:data.records()) {
            var presence=portal.visits().snapshot().get(player.getUUID());
            if(portal.returnAnchor(player.getUUID())==null || presence==null || presence==VisitLedger.Presence.EXITED)continue;
            if(living || GoldPortalService.inside(portal,player)) {
                portal.livingVisit(player.getUUID(),living);changed=true;
            }
        }
        if(changed) {
            data.setDirty();
            try { PortalCheckpoint.write(player.server,data); }
            catch(java.io.IOException failure){ExpeditionBridge.LOGGER.error("Cannot checkpoint living-space exploration context",failure);}
        }
    }
}
