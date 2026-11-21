package dev.expedition.bridge.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EntranceWritePolicyTest {
    private final PortalRecord.Site entrance=new PortalRecord.Site("minecraft:overworld",7,64,-8);
    private PortalRecord record(NaturalEntryPlan.Type type,PortalRecord.Phase phase) {
        return new PortalRecord(UUID.randomUUID(),type.kind,type.tier,entrance,1,phase,null,"",new VisitLedger(),Set.of(),"");
    }
    @Test void allSevenEntrancesMayInstallOnlyTheirExactTwoHalvesOnEmptyTerrain() {
        for(var type:NaturalEntryPlan.Type.values()) {
            var portal=record(type,PortalRecord.Phase.NEW);
            for(String air:List.of("minecraft:air","minecraft:cave_air","minecraft:void_air")) {
                assertTrue(EntranceWritePolicy.allows(portal,entrance,air,type.block));
                assertTrue(EntranceWritePolicy.allows(portal,new PortalRecord.Site(entrance.dimension(),7,65,-8),air,type.block));
            }
            assertFalse(EntranceWritePolicy.allows(portal,entrance,"minecraft:stone",type.block));
            assertFalse(EntranceWritePolicy.allows(portal,entrance,"minecraft:air","minecraft:oak_door"));
        }
    }
    @Test void scopeDoesNotCoverSupportsNeighborsOrOtherDimensions() {
        var type=NaturalEntryPlan.Type.DARK_OAK;var portal=record(type,PortalRecord.Phase.NEW);
        for(var site:List.of(new PortalRecord.Site(entrance.dimension(),7,63,-8),new PortalRecord.Site(entrance.dimension(),7,66,-8),
                new PortalRecord.Site(entrance.dimension(),8,64,-8),new PortalRecord.Site("dimdoors:dungeon",7,64,-8)))
            assertFalse(EntranceWritePolicy.allows(portal,site,"minecraft:air",type.block));
    }
    @Test void onlyClosingSpecialEntrancesMayBeRemoved() {
        for(var type:NaturalEntryPlan.Type.values()) {
            var portal=record(type,PortalRecord.Phase.CLOSING);
            assertEquals(type.kind.closesAfterVisit(),EntranceWritePolicy.allows(portal,entrance,type.block,"minecraft:air"));
            assertFalse(EntranceWritePolicy.allows(portal,entrance,"minecraft:stone","minecraft:air"));
            assertFalse(EntranceWritePolicy.allows(portal,entrance,type.block,"minecraft:stone"));
        }
    }
    @Test void failedOrClosedRecordsNeverGrantNewConstructionOrCleanup() {
        for(var phase:List.of(PortalRecord.Phase.FAILED,PortalRecord.Phase.CLOSED,PortalRecord.Phase.PREPARING)) {
            var portal=record(NaturalEntryPlan.Type.GOLD,phase);
            assertFalse(EntranceWritePolicy.allows(portal,entrance,"minecraft:air",NaturalEntryPlan.Type.GOLD.block));
            assertFalse(EntranceWritePolicy.allows(portal,entrance,NaturalEntryPlan.Type.GOLD.block,"minecraft:air"));
        }
    }
}
