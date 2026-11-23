package dev.expedition.bridge.diagnostics;

import com.google.gson.*;
import dev.expedition.bridge.domain.BlockBounds;
import java.time.Instant;
import java.util.*;

/** Bounded per-stage measurements, not a build journal, progress percentage or acceptance verdict. */
public final class WdaBuildReport {
    private record Slice(String stage,long start,long budget){}
    private static final class Timing {
        long samples,total,max,overBudget,budget;
    }
    private final UUID run=UUID.randomUUID(),portal;
    private final JsonObject identity=new JsonObject();
    private final Map<String,Timing> timings=new LinkedHashMap<>();
    private final long started;
    private Long finished;
    private Slice slice;
    private String state="RUNNING",stage="PREPARATION",portalPhase="NEW",failure;
    private JsonObject progress=new JsonObject();
    public WdaBuildReport(UUID portal,String target,long seed,long now) {
        this.portal=Objects.requireNonNull(portal);started=now;
        identity.addProperty("reportSchema",1);identity.addProperty("runId",run.toString());
        identity.addProperty("portal",portal.toString());identity.addProperty("structure",target);identity.addProperty("seed",seed);
        identity.addProperty("startedAtUtc",Instant.now().toString());identity.addProperty("runtimeAcceptancePassed",false);
    }
    public UUID runId(){return run;}
    public UUID portalId(){return portal;}
    public void begin(String stage,long now,long softBudgetNanos) {
        if(finished!=null || slice!=null)throw new IllegalStateException("Nested or finished measurement");
        if(softBudgetNanos<0)throw new IllegalArgumentException("Negative budget");
        this.stage=Objects.requireNonNull(stage);slice=new Slice(stage,now,softBudgetNanos);
    }
    public void end(long now) {
        if(slice==null)return;
        long duration=now-slice.start();if(duration<0)throw new IllegalArgumentException("Backwards monotonic clock");
        var timing=timings.computeIfAbsent(slice.stage(),key->new Timing());
        timing.samples++;timing.total+=duration;timing.max=Math.max(timing.max,duration);timing.budget=slice.budget();
        if(slice.budget()>0 && duration>slice.budget())timing.overBudget++;
        slice=null;
    }
    public void planned(int pieces,BlockBounds building,BlockBounds reserved) {
        identity.addProperty("plannedPieceCount",pieces);identity.add("buildingBounds",new Gson().toJsonTree(building));
        identity.add("reservedBounds",new Gson().toJsonTree(reserved));
    }
    public void progress(String stage,long blocks,long placements,long totalChunks,long indexed,long fluids) {
        this.stage=stage;progress=new JsonObject();
        progress.addProperty("terrainBlocksWritten",blocks);progress.addProperty("structureChunksPlaced",placements);
        progress.addProperty("structureChunksTotal",totalChunks);progress.addProperty("structureChunksIndexed",indexed);
        progress.addProperty("deferredFluidTicksRemaining",fluids);
    }
    public void finish(String phase,String failure,long now) {
        if(finished!=null)throw new IllegalStateException("Report already finished");
        end(now);portalPhase=phase;this.failure=failure;finished=now;
        state=phase.equals("READY")?"READY":phase.equals("FAILED")?"FAILED":"FINALIZATION_INCOMPLETE";
    }
    public JsonObject snapshot(long now) {
        var root=identity.deepCopy();root.addProperty("state",state);root.addProperty("stage",stage);
        if(finished!=null)root.addProperty("portalPhaseAtFinish",portalPhase);
        root.addProperty("elapsedMilliseconds",((finished==null?now:finished)-started)/1_000_000.0);
        if(failure!=null)root.addProperty("failure",failure);root.add("progress",progress.deepCopy());
        var stages=new JsonArray();
        timings.forEach((name,t)->{
            var row=new JsonObject();row.addProperty("stage",name);row.addProperty("samples",t.samples);
            row.addProperty("measuredMilliseconds",t.total/1_000_000.0);row.addProperty("maxSampleMilliseconds",t.max/1_000_000.0);
            row.addProperty("softBudgetMilliseconds",t.budget/1_000_000.0);row.addProperty("samplesOverSoftBudget",t.overBudget);
            stages.add(row);
        });root.add("timings",stages);
        var limits=new JsonArray();
        for(var limit:List.of("Stage counters are not proof of full building, traversal, entity or loot correctness",
                "No hard tick-time guarantee: native planning, chunk placement and final save can block",
                "Measured slices exclude report I/O; finalization includes exits, save barrier and waiting-player transit",
                "Process crash may leave no terminal report; never resume or regenerate from diagnostics",
                "No live block scan, chunk loading, gameplay mutation or RNG draw by snapshot"))limits.add(limit);
        root.add("limitations",limits);return root;
    }
}
