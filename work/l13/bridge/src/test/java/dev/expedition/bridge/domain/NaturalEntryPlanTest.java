package dev.expedition.bridge.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NaturalEntryPlanTest {
    @Test void approvedDefaultsProduceOneOrTwoUnbiasedAttempts() {
        var policy=NaturalEntryPlan.Policy.defaults();assertEquals(12,policy.regionChunks());assertEquals(48,policy.minDistance());
        var plan=new NaturalEntryPlan(42,policy);int two=0;
        for(int x=-5000;x<5000;x++) {int count=plan.region(x,13).size();assertTrue(count==1 || count==2);if(count==2)two++;}
        assertTrue(two>4800 && two<5200,"Equal one/two proposal distribution");
    }
    @Test void exactTypeWeightIntervalsMatchApprovedPercentages() {
        var weights=NaturalEntryPlan.Policy.defaults().weights();int[] counts=new int[7];
        for(int i=0;i<1000;i++)counts[NaturalEntryPlan.choose(weights,i).ordinal()]++;
        assertArrayEquals(new int[]{150,300,300,150,50,25,25},counts);
        assertThrows(IllegalArgumentException.class,()->NaturalEntryPlan.choose(weights,1000));
    }
    @Test void negativeCoordinatesAndChunkEdgesRemainInsideTheirRegion() {
        var plan=new NaturalEntryPlan(917,NaturalEntryPlan.Policy.defaults());
        for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)for(var c:plan.region(x,z)) {
            assertEquals(x,Math.floorDiv(c.chunkX(),12));assertEquals(z,Math.floorDiv(c.chunkZ(),12));
            assertTrue(Math.floorMod(c.x(),16)>=2 && Math.floorMod(c.x(),16)<=13);
            assertTrue(Math.floorMod(c.z(),16)>=2 && Math.floorMod(c.z(),16)<=13);
            assertTrue(plan.chunk(c.chunkX(),c.chunkZ()).contains(c));
        }
    }
    @Test void loadingOrderDoesNotChangeSurvivorsAndNeighborRegionsRespectDistance() {
        var plan=new NaturalEntryPlan(351,NaturalEntryPlan.Policy.defaults());var candidates=new ArrayList<NaturalEntryPlan.Candidate>();
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)candidates.addAll(plan.region(x,z));
        var first=candidates.stream().filter(plan::survivesSpacing).toList();Collections.shuffle(candidates,new Random(7));
        assertEquals(new HashSet<>(first),new HashSet<>(candidates.stream().filter(plan::survivesSpacing).toList()));
        assertTrue(first.size()<candidates.size(),"Exercise real spacing rejections");
        for(int i=0;i<first.size();i++)for(int j=i+1;j<first.size();j++) {
            var a=first.get(i);var b=first.get(j);assertFalse(NaturalEntryPlan.tooClose(a.x(),a.z(),b.x(),b.z(),48));
        }
    }
    @Test void typeWeightsDoNotMoveProposalsOrChangeSpacingPriorities() {
        var original=new NaturalEntryPlan(71,NaturalEntryPlan.Policy.defaults());
        var onlyGold=new NaturalEntryPlan(71,new NaturalEntryPlan.Policy(12,1,2,48,List.of(0,0,0,0,0,1,0)));
        for(int x=-10;x<10;x++) {
            var a=original.region(x,3);var b=onlyGold.region(x,3);assertEquals(a.size(),b.size());
            for(int i=0;i<a.size();i++) {
                assertEquals(a.get(i).id(),b.get(i).id());assertEquals(a.get(i).x(),b.get(i).x());assertEquals(a.get(i).z(),b.get(i).z());
                assertEquals(a.get(i).priority(),b.get(i).priority());assertEquals(NaturalEntryPlan.Type.GOLD,b.get(i).type());
            }
        }
    }
    @Test void spacingBoundaryAndLargeCoordinatesDoNotOverflow() {
        assertFalse(NaturalEntryPlan.tooClose(0,0,48,0,48));assertTrue(NaturalEntryPlan.tooClose(0,0,47,0,48));
        assertFalse(NaturalEntryPlan.tooClose(Integer.MIN_VALUE,0,Integer.MAX_VALUE,0,48));
        var plan=new NaturalEntryPlan(3,NaturalEntryPlan.Policy.defaults());
        assertEquals(plan.region(-156250,156250),new NaturalEntryPlan(3,NaturalEntryPlan.Policy.defaults()).region(-156250,156250));
    }
    @Test void invalidOrEmptyPoliciesCannotActivate() {
        assertThrows(IllegalArgumentException.class,()->new NaturalEntryPlan.Policy(12,2,1,48,List.of(1,1,1,1,1,1,1)));
        assertThrows(IllegalArgumentException.class,()->new NaturalEntryPlan.Policy(12,1,2,48,List.of(0,0,0,0,0,0,0)));
    }
}
