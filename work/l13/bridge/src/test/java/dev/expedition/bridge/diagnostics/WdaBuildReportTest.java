package dev.expedition.bridge.diagnostics;

import dev.expedition.bridge.domain.BlockBounds;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WdaBuildReportTest {
    private WdaBuildReport report(){return new WdaBuildReport(UUID.randomUUID(),"dungeons_arise:merchant_campsite",42,0);}
    @Test void softBudgetCountsActualOverrunsWithoutPretendingToEnforceAHardLimit() {
        var r=report();r.begin("STRUCTURE",0,4_000_000);r.end(9_000_000);
        r.begin("STRUCTURE",10_000_000,4_000_000);r.end(14_000_000);
        var t=r.snapshot(20_000_000).getAsJsonArray("timings").get(0).getAsJsonObject();
        assertEquals(2,t.get("samples").getAsInt());assertEquals(13,t.get("measuredMilliseconds").getAsDouble());
        assertEquals(9,t.get("maxSampleMilliseconds").getAsDouble());assertEquals(1,t.get("samplesOverSoftBudget").getAsInt());
    }
    @Test void planningAndFinalSaveRemainSeparatelyMeasuredWithoutClaimingBudgetCompliance() {
        var r=report();r.begin("PLANNING",0,0);r.end(90_000_000);
        r.begin("FINALIZATION",100_000_000,0);r.end(400_000_000);r.finish("READY",null,500_000_000);
        var json=r.snapshot(1_000_000_000);assertEquals(500,json.get("elapsedMilliseconds").getAsDouble());
        assertEquals("READY",json.get("state").getAsString());assertFalse(json.get("runtimeAcceptancePassed").getAsBoolean());
        var timings=json.getAsJsonArray("timings");assertEquals(2,timings.size());
        assertEquals(300,timings.get(1).getAsJsonObject().get("maxSampleMilliseconds").getAsDouble());
        assertEquals(0,timings.get(1).getAsJsonObject().get("softBudgetMilliseconds").getAsInt());
    }
    @Test void contentReadyDoesNotImplyPortalReadyOrSuccessfulAcceptance() {
        var r=report();r.progress("CONTENT_READY",7000,4,4,4,0);
        assertEquals("RUNNING",r.snapshot(5).get("state").getAsString());
        r.finish("GENERATING",null,10);var json=r.snapshot(20);
        assertEquals("FINALIZATION_INCOMPLETE",json.get("state").getAsString());
        assertEquals("GENERATING",json.get("portalPhaseAtFinish").getAsString());
        assertFalse(json.get("runtimeAcceptancePassed").getAsBoolean());
    }
    @Test void failedPlanningHasNoInventedDestinationOrCompletedChunkCounts() {
        var r=report();r.begin("PLANNING",0,0);r.finish("FAILED","height limit",100);
        var json=r.snapshot(200);assertEquals("FAILED",json.get("state").getAsString());
        assertEquals("PLANNING",json.get("stage").getAsString());assertEquals("height limit",json.get("failure").getAsString());
        assertFalse(json.has("reservedBounds"));assertTrue(json.getAsJsonObject("progress").entrySet().isEmpty());
        assertEquals(1,json.getAsJsonArray("timings").size());
    }
    @Test void observationsAreCopiesAndCountersDescribePartialWorkOnly() {
        var r=report();var bounds=new BlockBounds(0,0,0,31,31,31);r.planned(8,bounds,bounds.expand(16));
        r.progress("STRUCTURE",1000,1,4,0,25);var first=r.snapshot(10);
        first.getAsJsonObject("progress").addProperty("structureChunksPlaced",999);
        first.getAsJsonObject("buildingBounds").addProperty("minX",999);
        r.progress("INDEX",1000,4,4,2,25);var next=r.snapshot(20);
        assertEquals(4,next.getAsJsonObject("progress").get("structureChunksPlaced").getAsLong());
        assertEquals(2,next.getAsJsonObject("progress").get("structureChunksIndexed").getAsLong());
        assertEquals(0,next.getAsJsonObject("buildingBounds").get("minX").getAsInt());
        assertEquals(25,next.getAsJsonObject("progress").get("deferredFluidTicksRemaining").getAsLong());
    }
    @Test void longBuildKeepsOneAggregatePerStageInsteadOfEveryTick() {
        var r=report();for(int i=0;i<10000;i++){r.begin("ISLAND",i*100L,4);r.end(i*100L+5);}
        var samples=r.snapshot(2_000_000).getAsJsonArray("timings");assertEquals(1,samples.size());
        assertEquals(10000,samples.get(0).getAsJsonObject().get("samples").getAsInt());
        assertEquals(10000,samples.get(0).getAsJsonObject().get("samplesOverSoftBudget").getAsInt());
    }
    @Test void stoppingKeepsLastWorkAndReportCannotRestartGeneration() {
        var r=report();r.progress("STRUCTURE",2000,2,7,0,0);
        r.finish("FAILED","Server stopped during generation",100);
        assertEquals(2,r.snapshot(200).getAsJsonObject("progress").get("structureChunksPlaced").getAsInt());
        assertThrows(IllegalStateException.class,()->r.begin("STRUCTURE",300,4));
        assertThrows(IllegalStateException.class,()->r.finish("READY",null,300));
    }
}
