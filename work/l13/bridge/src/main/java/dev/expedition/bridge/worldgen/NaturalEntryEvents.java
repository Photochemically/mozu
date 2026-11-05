package dev.expedition.bridge.worldgen;

import dev.expedition.bridge.ExpeditionBridge;
import dev.expedition.bridge.content.DungeonCatalogLoader;
import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.portal.NaturalEntranceFactory;
import dev.expedition.bridge.storage.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;
import java.util.concurrent.*;

/** New full chunks only; pending candidates survive unload, consumed attempts never regenerate after restart. */
public final class NaturalEntryEvents {
    private record Observed(long chunk,boolean fresh){}
    private final Map<MinecraftServer,ConcurrentLinkedQueue<Observed>> observed=new ConcurrentHashMap<>();
    private final Map<MinecraftServer,LinkedHashSet<Long>> queues=new IdentityHashMap<>();
    private final Set<MinecraftServer> initialized=Collections.newSetFromMap(new IdentityHashMap<>());
    @SubscribeEvent public void starting(ServerAboutToStartEvent event) {
        if(!NaturalEntryConfig.enabled())return;
        NaturalEntryConfig.policy();NaturalEntranceFactory.validateBlocks();
        if(!DungeonCatalogLoader.current().enabled())throw new IllegalStateException("Natural entrances require a complete enabled dungeon catalog; refusing a partially enabled world");
    }
    @SubscribeEvent public void loaded(ChunkEvent.Load event) {
        if(!NaturalEntryConfig.enabled() || !(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)
                || !(event.getChunk() instanceof LevelChunk chunk))return;
        // Forge warns that chunks may not be FULL here. No world queries, SavedData access, writes or chunk references retained.
        observed.computeIfAbsent(level.getServer(),s->new ConcurrentLinkedQueue<>()).add(new Observed(chunk.getPos().toLong(),event.isNewChunk()));
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !NaturalEntryConfig.enabled())return;
        var server=event.getServer();var level=server.overworld();var data=ExpeditionSavedData.get(server);
        var policy=data.initializeNaturalPolicy(NaturalEntryConfig.policy());var plan=new NaturalEntryPlan(level.getSeed(),policy);
        if(initialized.add(server) && !policy.equals(NaturalEntryConfig.policy()))
            ExpeditionBridge.LOGGER.warn("This save keeps its pinned natural entrance layout; changed config applies to fresh worlds only");
        var incoming=observed.get(server);
        if(incoming!=null)for(int i=0;i<64;i++) {
            var load=incoming.poll();if(load==null)break;
            if(plan.chunk(ChunkPos.getX(load.chunk()),ChunkPos.getZ(load.chunk())).isEmpty())continue;
            if(load.fresh())data.naturalPending(load.chunk(),true);
            if(data.naturalPending(load.chunk()))queues.computeIfAbsent(server,s->new LinkedHashSet<>()).add(load.chunk());
        }
        var queue=queues.get(server);if(queue==null || queue.isEmpty())return;
        // One candidate chunk per tick; no getChunk/force-load calls for abandoned work.
        var iterator=queue.iterator();long key=iterator.next();iterator.remove();
        var chunk=level.getChunkSource().getChunkNow(ChunkPos.getX(key),ChunkPos.getZ(key));if(chunk==null)return;
        try {
            if(!DungeonCatalogLoader.current().enabled())return; // Keep pending metadata for a later load; never select an alternative pool.
            for(var candidate:plan.chunk(chunk.getPos().x,chunk.getPos().z)) {
                if(data.naturalAttempted(candidate.id()))continue;
                data.recordNaturalAttempt(candidate.id());PortalCheckpoint.write(server,data);
                if(!plan.survivesSpacing(candidate))continue;
                boolean nearby=data.records().stream().anyMatch(p->p.entrance().dimension().equals("minecraft:overworld")
                        && NaturalEntryPlan.tooClose(candidate.x(),candidate.z(),p.entrance().x(),p.entrance().z(),policy.minDistance()));
                if(nearby)continue; // Includes closed/failed entrances; closing does not open a refill slot.
                var site=NaturalEntranceSite.find(level,candidate.x(),candidate.z());if(site.isEmpty())continue;
                NaturalEntranceFactory.place(level,site.get(),candidate);
            }
            data.naturalPending(key,false);
        } catch(Exception failure) {
            // Consumed attempt and any allocated identity stay intact; never replay a partially written entrance.
            ExpeditionBridge.LOGGER.error("Natural entrance chunk {} failed; retained attempt journal, no automatic rebuild",chunk.getPos(),failure);
        }
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event){observed.remove(event.getServer());queues.remove(event.getServer());initialized.remove(event.getServer());}
}
