package dev.expedition.bridge.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DungeonCatalogTest {
    private DungeonSpec nativeRoom(String id,int weight) {
        return new DungeonSpec(id,DungeonSpec.Provider.NATIVE,"dimdoors:dungeon/test",Set.of(1,2,3,4,5),weight,null,0);
    }
    @Test void nativeAndWdaShareOneDeterministicCandidateSetIndependentOfResourceOrder() {
        var nativeRoom=nativeRoom("test:native",3);
        var wda=new DungeonSpec("test:wda",DungeonSpec.Provider.WDA,"dungeons_arise:test",Set.of(1,2,3,4,5),4,
                new DungeonSpec.Environment(80,8,8,24,IslandPlan.Surface.GRASS),2);
        var a=new DungeonCatalog(true,List.of(nativeRoom,wda),List.of(1,1,1,1,1));
        var b=new DungeonCatalog(true,List.of(wda,nativeRoom),List.of(1,1,1,1,1));
        var seen=new HashSet<DungeonSpec.Provider>();
        for(int seed=0;seed<100;seed++) {
            var choice=a.select(PortalKind.EXPLORATION,3,seed);assertEquals(choice,b.select(PortalKind.EXPLORATION,3,seed));seen.add(choice.content().provider());
        }
        assertEquals(Set.of(DungeonSpec.Provider.WDA,DungeonSpec.Provider.NATIVE),seen);
    }
    @Test void weightedBoundariesNeverSelectZeroWeightOrExceedTheTotal() {
        var weights=List.of(0,2,0,3,0);
        assertEquals(1,DungeonCatalog.weighted(weights,0));assertEquals(1,DungeonCatalog.weighted(weights,1));
        assertEquals(3,DungeonCatalog.weighted(weights,2));assertEquals(3,DungeonCatalog.weighted(weights,4));
        assertThrows(IllegalArgumentException.class,()->DungeonCatalog.weighted(weights,5));
    }
    @Test void enablingNeedsAllFivePoolsAndExplicitUnstableWeights() {
        assertThrows(IllegalArgumentException.class,()->new DungeonCatalog(true,List.of(nativeRoom("test:one",1)),List.of()));
        var oneTier=new DungeonSpec("test:one",DungeonSpec.Provider.NATIVE,"dimdoors:dungeon/test",Set.of(1),1,null,0);
        assertThrows(IllegalArgumentException.class,()->new DungeonCatalog(true,List.of(oneTier),List.of(1,1,1,1,1)));
        assertThrows(IllegalStateException.class,()->DungeonCatalog.disabled().select(PortalKind.EXPLORATION,1,1));
    }
    @Test void unstableUsesConfiguredTierAndGoldNeverEntersThePool() {
        var catalog=new DungeonCatalog(true,List.of(nativeRoom("test:one",1)),List.of(0,0,1,0,0));
        for(int i=0;i<10;i++)assertEquals(3,catalog.select(PortalKind.UNSTABLE,0,i).tier());
        assertThrows(IllegalStateException.class,()->catalog.select(PortalKind.GOLD,0,1));
    }
    @Test void selectedContentCannotBeReplacedOrMutatedByReload() {
        var portal=new PortalRecord(UUID.randomUUID(),PortalKind.UNSTABLE,0,new PortalRecord.Site("minecraft:overworld",0,70,0),42);
        var one=new DungeonCatalog.Choice(2,nativeRoom("test:one",1));var two=new DungeonCatalog.Choice(4,nativeRoom("test:two",1));
        portal.selectContent(one);portal.selectContent(one);assertEquals(2,portal.effectiveTier());
        assertThrows(IllegalStateException.class,()->portal.selectContent(two));assertEquals(one,portal.selection());
        assertThrows(UnsupportedOperationException.class,()->one.content().tiers().clear());
    }
    @Test void invalidProviderEnvironmentAndEntranceTierAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new DungeonSpec("test:bad",DungeonSpec.Provider.WDA,"dungeons_arise:test",Set.of(1),1,null,1));
        var portal=new PortalRecord(UUID.randomUUID(),PortalKind.EXPLORATION,1,new PortalRecord.Site("minecraft:overworld",0,70,0),1);
        assertThrows(IllegalArgumentException.class,()->portal.selectContent(new DungeonCatalog.Choice(5,nativeRoom("test:one",1))));
    }
}
