package dev.expedition.bridge.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WdaKnownResourceGapsTest {
    @Test void missingPoolsAndTemplatesAreNotSilentlyTreatedAsOptional() {
        var gaps=WdaKnownResourceGaps.missing("dungeons_arise:mechanical_nest",id->false,id->false);
        assertEquals(3,gaps.size());assertEquals(1,gaps.stream().filter(WdaKnownResourceGaps.Reference::pool).count());
    }
    @Test void resourcesSuppliedByAnExplicitRepairPackCanPassTheExistenceGate() {
        assertTrue(WdaKnownResourceGaps.missing("dungeons_arise:mechanical_nest",id->true,id->true).isEmpty());
        assertEquals(2,WdaKnownResourceGaps.missing("dungeons_arise:mechanical_nest",id->true,id->false).size());
    }
    @Test void auditDoesNotBanUnrelatedStructuresOrPretendToAuditUnknownResources() {
        assertTrue(WdaKnownResourceGaps.missing("dungeons_arise:lighthouse",id->false,id->false).isEmpty());
        assertEquals(1,WdaKnownResourceGaps.missing("dungeons_arise:foundry",id->false,id->true).size());
    }
}
