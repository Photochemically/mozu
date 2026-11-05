package dev.expedition.bridge.portal;

import dev.expedition.bridge.ExpeditionBridge;
import dev.expedition.bridge.content.*;
import dev.expedition.bridge.content.wda.*;
import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.storage.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.dimdev.dimdoors.api.util.Location;
import java.util.*;

/** One active build per server; visitors keep their own persisted operation while sharing one pinned instance. */
public final class FirstVisitService {
    public static final FirstVisitService INSTANCE=new FirstVisitService();
    private record Build(PortalRecord portal,NativeRoomBuild nativeRoom){}
    private final Map<MinecraftServer,Build> active=new IdentityHashMap<>();
    private FirstVisitService(){}

    public boolean enter(ServerPlayer player,UUID id,Location source) {
        var server=player.server;var data=ExpeditionSavedData.get(server);var portal=data.get(id);
        if(portal==null || portal.kind()==PortalKind.GOLD || source==null || !portal.entrance().equals(GoldPortalService.site(source))
                || !nearEntrance(player,portal))return false;
        if(portal.phase()!=PortalRecord.Phase.NEW && portal.phase()!=PortalRecord.Phase.GENERATING && portal.phase()!=PortalRecord.Phase.READY)return false;
        if(portal.phase()==PortalRecord.Phase.NEW && portal.selection()==null && !DungeonCatalogLoader.current().enabled()) {
            message(player,"这扇探索门尚未开放。");return false;
        }
        try {
            var operation=ManagedTransit.beginEntry(player,portal);
            // A Limbo diversion never pins content, allocates a pocket, or starts a building job.
            if(operation.state()==TravelOperation.State.LANDED || operation.outcome()==TravelOperation.Outcome.LIMBO)
                return ManagedTransit.enterResolved(player,portal,operation);
            if(portal.phase()==PortalRecord.Phase.READY)return ManagedTransit.enterResolved(player,portal,operation);
            if(portal.phase()==PortalRecord.Phase.NEW) {
                if(portal.selection()==null) {
                    portal.selectContent(DungeonCatalogLoader.current().select(portal.kind(),portal.tier(),portal.seed()));
                    data.setDirty();PortalCheckpoint.write(server,data);
                }
                if(active.containsKey(server) || WdaBuildQueue.INSTANCE.busy(server)) {
                    message(player,"另一处空间正在形成，请稍后重试。");return false;
                }
                // Reject unreviewed native configurations while the instance is still NEW, before preparation.
                if(portal.selection().content().provider()==DungeonSpec.Provider.NATIVE)NativeRoomProvider.validate(portal.selection().content());
                start(server,portal);
            }
            message(player,"空间正在形成，请在门旁稍候。");return false;
        } catch(Exception failure) {
            ExpeditionBridge.LOGGER.error("Exploration entrance {} unavailable; selection and travel outcomes retained",id,failure);
            message(player,"空间暂不可用。已有地牢不会被重置。");return false;
        }
    }
    private void start(MinecraftServer server,PortalRecord portal) throws Exception {
        var data=ExpeditionSavedData.get(server);var spec=portal.selection().content();
        active.put(server,new Build(portal,null));
        try {
            if(spec.provider()==DungeonSpec.Provider.NATIVE) {
                portal.preparationStarted(spec.target());data.setDirty();PortalCheckpoint.write(server,data);
                new NativeRoomProvider().generate(server,portal,spec);
                portal.generationStarted();data.setDirty();PortalCheckpoint.write(server,data);
                active.put(server,new Build(portal,new NativeRoomBuild(server,portal)));
            } else {
                var e=spec.environment();
                WdaBuildQueue.INSTANCE.enqueue(server,portal,ResourceLocation.parse(spec.target()),
                        new WdaStructurePlanner.Environment(e.firstFreeY(),e.padding(),e.rim(),e.foundationDepth(),e.surface(),e.waterDepth(),e.boarding(),e.entryMode()))
                        .whenComplete((result,error)->{
                            if(error!=null){failed(server,portal,error);return;}
                            try {
                                if(e.entryMode().interior())new WdaExitInstaller().installInterior(server,portal,result.interiorSites());
                                else new WdaExitInstaller().install(server,portal,result.island(),spec.exits());
                                ready(server,portal);
                            } catch(Exception failure){failed(server,portal,failure);}
                        });
            }
        } catch(Exception failure){failed(server,portal,failure);throw failure;}
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var server=event.getServer();var build=active.get(server);
        if(build==null || build.nativeRoom()==null)return;
        try { if(build.nativeRoom().step())ready(server,build.portal()); }
        catch(Exception failure){failed(server,build.portal(),failure);}
    }
    private void ready(MinecraftServer server,PortalRecord portal) throws Exception {
        GenerationActivation.activate(server,portal);active.remove(server);
        var data=ExpeditionSavedData.get(server);
        for(var op:data.travel().operations()) {
            if(!op.portal().equals(portal.id()) || !op.route().entering() || op.route().living()
                    || op.state()!=TravelOperation.State.PREPARED || op.outcome()!=TravelOperation.Outcome.DESTINATION)continue;
            var player=server.getPlayerList().getPlayer(op.player());
            if(player==null || !player.isAlive() || portal.visits().snapshot().get(op.player())!=VisitLedger.Presence.ENTERING || !nearEntrance(player,portal))continue;
            try { ManagedTransit.enterResolved(player,portal,op); }
            catch(Exception failure){ExpeditionBridge.LOGGER.error("Prepared instance {} remains ready; player transit can retry",portal.id(),failure);}
        }
    }
    private void failed(MinecraftServer server,PortalRecord portal,Throwable failure) {
        var build=active.get(server);if(build!=null && build.portal().id().equals(portal.id()))active.remove(server);
        if(portal.phase()!=PortalRecord.Phase.FAILED)portal.quarantine(failure.toString());
        var data=ExpeditionSavedData.get(server);data.setDirty();
        try{PortalCheckpoint.write(server,data);}catch(java.io.IOException save){failure.addSuppressed(save);}
        ExpeditionBridge.LOGGER.error("Instance {} quarantined; no content replacement, regeneration or cleanup",portal.id(),failure);
    }
    @SubscribeEvent public void stopping(ServerStoppingEvent event) {
        var build=active.get(event.getServer());
        if(build!=null)failed(event.getServer(),build.portal(),new IllegalStateException("Stopped during first-visit generation"));
    }
    public static boolean nearEntrance(ServerPlayer player,PortalRecord portal) {
        var a=portal.entrance();var p=player.blockPosition();
        return player.level().dimension().location().toString().equals(a.dimension()) && Math.abs((long)p.getX()-a.x())<=3
                && Math.abs((long)p.getY()-a.y())<=3 && Math.abs((long)p.getZ()-a.z())<=3;
    }
    private static void message(ServerPlayer player,String text){player.displayClientMessage(Component.literal(text),true);}
}
