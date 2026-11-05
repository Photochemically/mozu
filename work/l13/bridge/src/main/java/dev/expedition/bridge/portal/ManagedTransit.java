package dev.expedition.bridge.portal;

import dev.expedition.bridge.ExpeditionBridge;
import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.storage.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.dimdev.dimdoors.api.util.Location;
import org.dimdev.dimdoors.world.level.registry.DimensionalRegistry;
import java.io.IOException;
import java.util.UUID;

/** Shared journaled travel. Content generation belongs to FirstVisitService, never to the landing routine. */
public final class ManagedTransit {
    private ManagedTransit(){}
    public static boolean enterGold(ServerPlayer player,PortalRecord portal) throws IOException {
        return enterResolved(player,portal,beginEntry(player,portal));
    }
    public static TravelOperation beginEntry(ServerPlayer player,PortalRecord portal) throws IOException {
        var data=ExpeditionSavedData.get(player.server);
        var op=data.travel().begin(player.getUUID(),portal.id(),TravelRoute.of(portal.kind(),true),portal.entrance(),portal.entrance(),
                ()->player.server.overworld().getRandom().nextInt(10000));
        if(op.state()==TravelOperation.State.PREPARED) {
            portal.bindReturnAnchor(player.getUUID(),op.anchor());portal.visits().entryIntent(player.getUUID());
            data.setDirty();PortalCheckpoint.write(player.server,data);
        }
        return op;
    }
    public static boolean enterResolved(ServerPlayer player,PortalRecord portal,TravelOperation op) throws IOException {
        if(op.state()==TravelOperation.State.LANDED)return true;
        if(op.outcome()==TravelOperation.Outcome.LIMBO)return divert(player,portal,op);
        if(portal.phase()!=PortalRecord.Phase.READY || !GoldPortalService.nativeIdentityMatches(player.server,portal))return false;
        var arrival=portal.destination().arrival();var world=player.server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(arrival.dimension())));
        if(world==null)return false;
        var landing=SafeLanding.nearConnection(player,world,new BlockPos(arrival.x(),arrival.y(),arrival.z()),portal.destination().bounds());
        if(landing.isEmpty()){unavailable(player);return false;}
        return perform(player,portal,op,landing.get());
    }
    public static boolean leave(ServerPlayer player,UUID portalId,Location source) {
        var data=ExpeditionSavedData.get(player.server);var portal=data.get(portalId);
        if(portal==null || source==null || !portal.exits().contains(GoldPortalService.site(source))
                || !GoldPortalService.inside(portal,player) || !GoldPortalService.nativeIdentityMatches(player.server,portal))return false;
        var physical=player.blockPosition();
        if(Math.abs((long)physical.getX()-source.pos.getX())>3 || Math.abs((long)physical.getY()-source.pos.getY())>3
                || Math.abs((long)physical.getZ()-source.pos.getZ())>3)return false;
        if(portal.phase()!=PortalRecord.Phase.READY && portal.phase()!=PortalRecord.Phase.CLOSING && portal.phase()!=PortalRecord.Phase.CLOSED)return false;
        try {
            // Existing-space transport may bring a visitor without using the overworld door. Its owning entrance is the fallback anchor.
            var anchor=portal.returnAnchor(player.getUUID());if(anchor==null){anchor=portal.entrance();portal.bindReturnAnchor(player.getUUID(),anchor);}
            var op=data.travel().begin(player.getUUID(),portal.id(),TravelRoute.of(portal.kind(),false),GoldPortalService.site(source),anchor,
                    ()->player.server.overworld().getRandom().nextInt(10000));
            if(op.state()==TravelOperation.State.LANDED)return true;
            data.setDirty();PortalCheckpoint.write(player.server,data);
            if(op.outcome()==TravelOperation.Outcome.LIMBO)return divert(player,portal,op);
            var landing=SafeLanding.overworld(player,op.anchor(),op.id().getMostSignificantBits()^op.id().getLeastSignificantBits());
            if(landing.isEmpty()){unavailable(player);return false;}
            return perform(player,portal,op,landing.get());
        } catch(Exception failure) {
            ExpeditionBridge.LOGGER.error("Managed exit failed for {}; keeping persisted transit outcome",portalId,failure);
            unavailable(player);return false;
        }
    }
    private static boolean divert(ServerPlayer player,PortalRecord portal,TravelOperation op) throws IOException {
        var landing=SafeLanding.limbo(player,op.anchor(),op.id().getMostSignificantBits()^op.id().getLeastSignificantBits());
        if(landing.isEmpty()){unavailable(player);return false;}
        var a=op.anchor();
        DimensionalRegistry.getRiftRegistry().setOverworldRift(player.getUUID(),new Location(player.server.overworld(),new BlockPos(a.x(),a.y(),a.z())));
        return perform(player,portal,op,landing.get());
    }
    private static boolean perform(ServerPlayer player,PortalRecord portal,TravelOperation op,PortalRecord.Site landing) throws IOException {
        var world=player.server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(landing.dimension())));
        if(world==null || !SafeLanding.safe(player,world,new BlockPos(landing.x(),landing.y(),landing.z())))return false;
        var data=ExpeditionSavedData.get(player.server);op.bindLanding(landing);data.setDirty();PortalCheckpoint.write(player.server,data);
        player.getPersistentData().putUUID(TravelEvents.PLAYER_MARKER,op.id());
        player.stopRiding();player.teleportTo(world,landing.x()+0.5,landing.y(),landing.z()+0.5,player.getYRot(),player.getXRot());
        if(!atLanding(player,op))return false;
        player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;player.setPortalCooldown();
        // Persist actual player position before committing. A restart at the planned landing can reconcile an uncommitted operation.
        player.server.getPlayerList().saveAll();
        finish(player,portal,op);PortalCheckpoint.write(player.server,data);return true;
    }
    public static boolean atLanding(ServerPlayer player,TravelOperation op) {
        var p=op.landing();return p!=null && player.level().dimension().location().toString().equals(p.dimension())
                && Math.abs(player.getX()-(p.x()+0.5))<1 && Math.abs(player.getY()-p.y())<1 && Math.abs(player.getZ()-(p.z()+0.5))<1;
    }
    public static void finish(ServerPlayer player,PortalRecord portal,TravelOperation op) {
        if(!atLanding(player,op))throw new IllegalStateException("Cannot commit transit without observed arrival");
        portal.bindReturnAnchor(player.getUUID(),op.anchor());portal.livingVisit(player.getUUID(),false);
        if(op.route().living())throw new IllegalArgumentException("Living-space travel uses native connected positions");
        if(op.outcome()==TravelOperation.Outcome.LIMBO)portal.visits().entered(player.getUUID());
        else if(op.route().entering())portal.entered(player.getUUID());
        else if(portal.visits().hasParticipant(player.getUUID()))portal.visits().confirmedSafeExit(player.getUUID());
        op.landed(player.server.overworld().getGameTime());ExpeditionSavedData.get(player.server).setDirty();
    }
    private static void unavailable(ServerPlayer player) {
        player.displayClientMessage(Component.literal("暂时无法完成安全传送，已保留当前位置和返回记录。请稍后重试。"),false);
    }
}
