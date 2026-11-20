package dev.expedition.bridge.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProtectedBlockPlanTest {
    private PortalRecord.Site site(int x,int y,int z){return new PortalRecord.Site("dimdoors:dungeon_pockets",x,y,z);}
    @Test void entranceInternalSourceAndOneWayDestinationIncludeHalvesAndSupportsOnly() {
        var entry=site(2,2,2);var source=site(8,2,8);var target=site(12,2,12);
        var d=new PortalRecord.Destination(entry.dimension(),1,new BlockBounds(0,0,0,20,20,20),entry);
        var result=ProtectedBlockPlan.routes(d,new NativeRiftPlan(Set.of(entry),Map.of(source,target)));
        assertEquals(9,result.size());
        for(var p:List.of(entry,source,target))for(int dy=-1;dy<=1;dy++)assertTrue(result.contains(site(p.x(),p.y()+dy,p.z())));
        assertFalse(result.contains(site(8,2,9)));assertFalse(result.contains(site(12,4,12)));
        assertThrows(IllegalArgumentException.class,()->ProtectedBlockPlan.routes(d,new NativeRiftPlan(Set.of(site(21,2,2)),Map.of())));
    }
    @Test void boundaryPlansNeverClaimAnotherPocketAndDeduplicateSharedEndpoints() {
        var entry=site(0,0,0);var second=site(0,1,0);
        var d=new PortalRecord.Destination(entry.dimension(),1,new BlockBounds(0,0,0,3,3,3),entry);
        assertEquals(Set.of(entry,second,site(0,2,0)),ProtectedBlockPlan.routes(d,new NativeRiftPlan(Set.of(entry),Map.of(second,entry))));
    }
}
