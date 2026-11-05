package dev.expedition.bridge.diagnostics;

import dev.expedition.bridge.content.wda.WdaStructurePlanner;
import dev.expedition.bridge.domain.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PlanAuditReportTest {
    private PlanAuditReport report(){return new PlanAuditReport("test",List.of("dungeons_arise:a","dungeons_arise:b"),1,
            new WdaStructurePlanner.Environment(96,8,8,32,IslandPlan.Surface.GRASS));}
    private WdaStructurePlanner.Inspection inspection(String id,boolean fits) {
        var bounds=new BlockBounds(0,0,0,20,100,20);
        return new WdaStructurePlanner.Inspection(id,1,5,0,bounds,bounds,fits?133:300,256,fits,4000,4);
    }
    @Test void fittingAndOversizedPlansDoNotClaimRuntimeAcceptance() {
        var r=report();r.record("dungeons_arise:a",inspection("dungeons_arise:a",true),null,1000);
        r.record("dungeons_arise:b",inspection("dungeons_arise:b",false),null,2000);r.finish();var json=r.snapshot();
        assertEquals("finished",json.get("state").getAsString());assertEquals(1,json.get("heightLimits").getAsInt());
        assertEquals(1,json.get("plannedHeightFits").getAsInt());assertFalse(json.get("runtimeAcceptancePassed").getAsBoolean());
        assertTrue(json.getAsJsonArray("notChecked").isEmpty());
    }
    @Test void cancelledRunKeepsUncheckedAndFailureSeparate() {
        var r=report();r.record("dungeons_arise:a",null,"missing template",0);r.cancel();var json=r.snapshot();
        assertEquals("cancelled",json.get("state").getAsString());assertEquals(1,json.get("errors").getAsInt());
        assertEquals("dungeons_arise:b",json.getAsJsonArray("notChecked").get(0).getAsString());
        assertThrows(IllegalStateException.class,r::finish);
    }
    @Test void duplicateAndWrongIdentityCannotHideUncheckedItems() {
        var r=report();assertThrows(IllegalArgumentException.class,()->r.record("dungeons_arise:a",inspection("dungeons_arise:b",true),null,0));
        assertThrows(IllegalStateException.class,r::finish);r.record("dungeons_arise:a",null,"failed",0);
        assertThrows(IllegalStateException.class,()->r.record("dungeons_arise:a",null,"failed again",0));
    }
}
