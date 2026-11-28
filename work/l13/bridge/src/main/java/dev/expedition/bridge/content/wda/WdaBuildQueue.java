package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.ExpeditionBridge;
import dev.expedition.bridge.content.*;
import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.storage.*;
import dev.expedition.bridge.diagnostics.WdaBuildReport;
import dev.expedition.bridge.diagnostics.WdaBuildReportWriter;
import com.google.gson.JsonObject;
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
    private record Pending(WdaBuildJob job,CompletableFuture<ContentResult> result,WdaBuildReport report) {}
    private final Map<MinecraftServer,Pending> pending=new IdentityHashMap<>();
    private WdaBuildQueue() {}
    public boolean busy(MinecraftServer server){return pending.containsKey(server);}
    public JsonObject activeReport(MinecraftServer server,UUID portal) {
        if(!server.isSameThread())throw new IllegalStateException("Build observation off server thread");
        var item=pending.get(server);
        return item==null || (portal!=null && !item.job.portal.id().equals(portal))?null:item.report.snapshot(System.nanoTime());
    }
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
        var report=new WdaBuildReport(portal.id(),structure.toString(),portal.seed(),System.nanoTime());
        boolean allocationCommitted=false;
        try {
            report.begin("PREPARATION",System.nanoTime(),0);
            portal.preparationStarted(structure.toString());PortalCheckpoint.write(server,data);
            report.end(System.nanoTime());report.begin("PLANNING",System.nanoTime(),0);
            long planningStarted=System.nanoTime();
            var plan=new WdaStructurePlanner().prepare(server,structure,portal.seed(),environment);
            report.end(System.nanoTime());report.begin("ALLOCATION",System.nanoTime(),0);
            ExpeditionBridge.LOGGER.info("WDA native planning for {}: {} pieces, {} ms, full bounds {}",
                    portal.id(),plan.start().getPieces().size(),(System.nanoTime()-planningStarted)/1_000_000.0,plan.island().completeBounds());
            var world=Objects.requireNonNull(server.getLevel(ModDimensions.DUNGEON));
            var e=portal.entrance();
            if(!e.dimension().equals("minecraft:overworld"))throw new IllegalArgumentException("Exploration anchor must be overworld");
            var virtual=VirtualLocation.fromLocation(new Location(server.overworld(),new BlockPos(e.x(),e.y(),e.z())));
            virtual=new VirtualLocation(virtual.getWorld(),virtual.getX(),virtual.getZ(),virtual.getDepth()+1);
            // From here on the allocator mutates DD registry state: any failure must quarantine, not retry.
            allocationCommitted=true;
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
            var job=new WdaBuildJob(world,portal,plan);
            report.planned(plan.start().getPieces().size(),plan.island().building(),reservation.allocation());
            report.end(System.nanoTime());job.describeProgress(report);
            pending.put(server,new Pending(job,result,report));
            return result;
        } catch(IOException|RuntimeException failure) {
            report.end(System.nanoTime());fail(server,portal,failure,allocationCommitted);finishReport(server,portal,report);
            throw failure;
        }
    }

    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var server=event.getServer();var item=pending.get(server);if(item==null)return;
        try {
            // Provisional technical work budget, independent of unapproved gameplay weights/difficulty.
            item.report.begin(item.job.stage().name(),System.nanoTime(),4_000_000);
            try { item.job.step(8192,4_000_000); }
            finally { item.report.end(System.nanoTime());item.job.describeProgress(item.report); }
            if(item.job.stage()==WdaBuildJob.Stage.CONTENT_READY) {
                pending.remove(server);
                var job=item.job;
                ExpeditionBridge.LOGGER.info("WDA content staged for {}: {} terrain blocks, {} placement chunks, slowest step {} ms; portal finalization still required",
                        job.portal.id(),job.blocks(),job.chunkPlacements(),job.maxUnitNanos()/1_000_000.0);
                item.report.begin("FINALIZATION",System.nanoTime(),0);
                // Existing synchronous completion installs exits, saves and activates the instance.
                item.result.complete(new ContentResult(job.portal.destination(),job.plan.island(),job.interiorSites(),job.blocks(),job.chunkPlacements(),job.maxUnitNanos()));
                item.report.end(System.nanoTime());finishReport(server,job.portal,item.report);
            }
        } catch(RuntimeException failure) {
            pending.remove(server);fail(server,item.job.portal,failure,true);item.result.completeExceptionally(failure);
            finishReport(server,item.job.portal,item.report);
        }
    }
    @SubscribeEvent public void stopping(ServerStoppingEvent event) {
        var item=pending.remove(event.getServer());if(item==null)return;
        var failure=new IllegalStateException("Server stopped during generation; reserved content retained, automatic replay disabled");
        fail(event.getServer(),item.job.portal,failure,true);item.result.completeExceptionally(failure);
        finishReport(event.getServer(),item.job.portal,item.report);
    }
    private void finishReport(MinecraftServer server,PortalRecord portal,WdaBuildReport report) {
        try {
            report.finish(portal.phase().name(),portal.failure(),System.nanoTime());
            var file=WdaBuildReportWriter.write(server.getWorldPath(LevelResource.ROOT).resolve("expedition_reports"),report,System.nanoTime());
            ExpeditionBridge.LOGGER.info("WDA build diagnostic saved for {}: {}; not an acceptance verdict",portal.id(),file);
        } catch(IOException|RuntimeException diagnosticFailure) {
            // Reports are not saved gameplay state. An unavailable report directory cannot undo READY or cause a replay.
            ExpeditionBridge.LOGGER.warn("Cannot save WDA build diagnostic for {}; gameplay state unchanged",portal.id(),diagnosticFailure);
        }
    }
    private void fail(MinecraftServer server,PortalRecord portal,Exception failure,boolean allocationCommitted) {
        if(portal.phase()!=PortalRecord.Phase.FAILED) {
            if(!allocationCommitted && portal.destination()==null
                    && (portal.phase()==PortalRecord.Phase.NEW || portal.phase()==PortalRecord.Phase.PREPARING))
                portal.retryableFailure(failure.toString());
            else portal.quarantine(failure.toString());
        }
        var data=ExpeditionSavedData.get(server);data.setDirty();
        try { PortalCheckpoint.write(server,data); } catch(IOException checkpoint) { failure.addSuppressed(checkpoint); }
        if(portal.phase()==PortalRecord.Phase.FAILED)
            ExpeditionBridge.LOGGER.error("WDA generation quarantined for {}; no automatic regeneration",portal.id(),failure);
        else
            ExpeditionBridge.LOGGER.warn("WDA generation failed before allocation for {}; retry {} of {}; pinned content, seed and travel outcomes retained",
                    portal.id(),portal.generationAttempts(),PortalRecord.MAX_GENERATION_ATTEMPTS,failure);
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
