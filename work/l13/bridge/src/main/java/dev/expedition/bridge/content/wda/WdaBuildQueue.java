package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.ExpeditionBridge;
import dev.expedition.bridge.content.*;
import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.storage.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.dimdev.dimdoors.api.util.Location;
import org.dimdev.dimdoors.world.ModDimensions;
import org.dimdev.dimdoors.world.pocket.VirtualLocation;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Internal content stage used by FirstVisitService. Completion alone does not activate the portal. */
public final class WdaBuildQueue {
    public static final WdaBuildQueue INSTANCE=new WdaBuildQueue();
    public record ContentResult(PortalRecord.Destination destination,IslandPlan island,List<PortalRecord.Site> interiorSites,long islandBlocks,long placementChunks,long slowestStepNanos) {
        public ContentResult {interiorSites=List.copyOf(interiorSites);}
    }
    private record Pending(WdaBuildJob job,CompletableFuture<ContentResult> result) {}
    private final Map<MinecraftServer,Pending> pending=new IdentityHashMap<>();
    private WdaBuildQueue() {}
    public boolean busy(MinecraftServer server){return pending.containsKey(server);}
    public boolean deferFluidTick(ServerLevel world,BlockPos pos,net.minecraft.world.level.material.FluidState state) {
        var item=pending.get(world.getServer());
        return item!=null && item.job.world==world && item.job.deferFluidTick(pos,state);
    }

    /**
     * Completes after terrain/structure/index creation, NOT after gameplay activation.
     * Caller must install/validate protected entrances/exits and persist native pocket/chunk data before READY.
     * A crash or abandoned finalization leaves GENERATING, which existing recovery quarantines without replay.
     */
    public CompletableFuture<ContentResult> enqueue(MinecraftServer server,PortalRecord portal,ResourceLocation structure,
                                                   WdaStructurePlanner.Environment environment) throws IOException {
        if(!server.isSameThread())throw new IllegalStateException("Generation enqueue off server thread");
        if(pending.containsKey(server))throw new IllegalStateException("Another dungeon is generating; retry without changing this entrance");
        var data=ExpeditionSavedData.get(server);
        if(data.get(portal.id())!=portal || portal.phase()!=PortalRecord.Phase.NEW || portal.kind()==PortalKind.GOLD)
            throw new IllegalArgumentException("Requires a registered, ungenerated exploration entrance");
        try {
            portal.preparationStarted(structure.toString());PortalCheckpoint.write(server,data);
            long planningStarted=System.nanoTime();
            var plan=new WdaStructurePlanner().prepare(server,structure,portal.seed(),environment);
            ExpeditionBridge.LOGGER.info("WDA native planning for {}: {} pieces, {} ms, full bounds {}",
                    portal.id(),plan.start().getPieces().size(),(System.nanoTime()-planningStarted)/1_000_000.0,plan.island().completeBounds());
            var world=Objects.requireNonNull(server.getLevel(ModDimensions.DUNGEON));
            var e=portal.entrance();
            if(!e.dimension().equals("minecraft:overworld"))throw new IllegalArgumentException("Exploration anchor must be overworld");
            var virtual=VirtualLocation.fromLocation(new Location(server.overworld(),new BlockPos(e.x(),e.y(),e.z())));
            virtual=new VirtualLocation(virtual.getWorld(),virtual.getX(),virtual.getZ(),virtual.getDepth()+1);
            var reservation=new NativePocketAllocator().reserve(world,plan.island().completeBounds(),16,virtual);
            if(!reservation.pocket().addAddon(new PocketIdentity(portal.id())))throw new IllegalStateException("Pocket identity refused");
            plan.relocateTo(reservation.content());
            var island=plan.island();String dimension=world.dimension().location().toString();
            var destination=new PortalRecord.Destination(dimension,reservation.pocket().getId(),reservation.allocation(),
                    new PortalRecord.Site(dimension,island.entryX(),island.entryY(),island.entryZ()));
            portal.reserve(structure.toString(),destination);PortalCheckpoint.write(server,data);
            writeManifest(server,portal,plan);
            portal.generationStarted();PortalCheckpoint.write(server,data);
            var result=new CompletableFuture<ContentResult>();
            pending.put(server,new Pending(new WdaBuildJob(world,portal,plan),result));
            return result;
        } catch(IOException|RuntimeException failure) {
            fail(server,portal,failure);
            throw failure;
        }
    }

    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var server=event.getServer();var item=pending.get(server);if(item==null)return;
        try {
            // Provisional technical work budget, independent of unapproved gameplay weights/difficulty.
            item.job.step(8192,4_000_000);
            if(item.job.stage()==WdaBuildJob.Stage.CONTENT_READY) {
                pending.remove(server);
                var job=item.job;
                ExpeditionBridge.LOGGER.info("WDA content staged for {}: {} terrain blocks, {} placement chunks, slowest step {} ms; portal finalization still required",
                        job.portal.id(),job.blocks(),job.chunkPlacements(),job.maxUnitNanos()/1_000_000.0);
                item.result.complete(new ContentResult(job.portal.destination(),job.plan.island(),job.interiorSites(),job.blocks(),job.chunkPlacements(),job.maxUnitNanos()));
            }
        } catch(RuntimeException failure) {
            pending.remove(server);fail(server,item.job.portal,failure);item.result.completeExceptionally(failure);
        }
    }
    @SubscribeEvent public void stopping(ServerStoppingEvent event) {
        var item=pending.remove(event.getServer());if(item==null)return;
        var failure=new IllegalStateException("Server stopped during generation; reserved content retained, automatic replay disabled");
        fail(event.getServer(),item.job.portal,failure);item.result.completeExceptionally(failure);
    }
    private void fail(MinecraftServer server,PortalRecord portal,Exception failure) {
        if(portal.phase()!=PortalRecord.Phase.FAILED)portal.quarantine(failure.toString());
        var data=ExpeditionSavedData.get(server);data.setDirty();
        try { PortalCheckpoint.write(server,data); } catch(IOException checkpoint) { failure.addSuppressed(checkpoint); }
        ExpeditionBridge.LOGGER.error("WDA generation quarantined for {}; no automatic regeneration",portal.id(),failure);
    }
    private void writeManifest(MinecraftServer server,PortalRecord portal,WdaStructurePlan plan) throws IOException {
        var dir=server.getWorldPath(LevelResource.ROOT).resolve("data/expedition_bridge_generation");Files.createDirectories(dir);
        var target=dir.resolve(portal.id()+".nbt");
        if(Files.exists(target))throw new IOException("Generation manifest already exists; refusing duplicate building generation");
        var tag=new CompoundTag();tag.putInt("Schema",1);tag.putUUID("Portal",portal.id());tag.putLong("Seed",portal.seed());
        tag.put("PortalRecord",PortalNbt.write(portal));
        tag.put("StructureStart",plan.start().createTag(StructurePieceSerializationContext.fromLevel(server.getLevel(ModDimensions.DUNGEON)),plan.start().getChunkPos()));
        var island=plan.island();var b=island.building();
        tag.putIntArray("BuildingBounds",new int[]{b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()});
        tag.putInt("StructureIndexVersion",1);
        tag.putInt("FirstFreeY",island.firstFreeY());tag.putInt("Padding",island.padding());tag.putInt("Rim",island.rim());
        tag.putInt("FoundationDepth",island.foundationDepth());tag.putString("Surface",island.surface().name());
        tag.putInt("WaterDepth",island.waterDepth());
        tag.putBoolean("Boarding",plan.boarding());
        tag.putString("EntryMode",plan.entryMode().name());
        var temporary=Files.createTempFile(dir,"generation-",".tmp");
        try { NbtIo.writeCompressed(tag,temporary.toFile());Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE); }
        finally { Files.deleteIfExists(temporary); }
    }
}
