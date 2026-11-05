package dev.expedition.bridge.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeRiftPlanTest {
    private final PortalRecord.Site arrival=p(0,1,0),a=p(5,2,6),b=p(8,3,9),chain=p(15,2,15);
    private PortalRecord.Site p(int x,int y,int z){return new PortalRecord.Site("dimdoors:dungeon_pockets",x,y,z);}
    private PortalRecord.Destination destination(){return new PortalRecord.Destination(arrival.dimension(),7,new BlockBounds(0,0,0,20,20,20),arrival);}
    @Test void localPuzzleConnectionsStayInsideWhileExternalRoutesBecomeExits() {
        var plan=NativeRiftPlan.create(destination(),Set.of(arrival,a,b,chain),Map.of(a,b,b,a,chain,p(400,2,400)),Set.of(),false);
        assertEquals(Map.of(a,b,b,a),plan.internalLinks());assertEquals(Set.of(arrival,chain),plan.exits());
    }
    @Test void brokenInternalLinksCannotQuietlyBecomeReturnDoors() {
        assertThrows(IllegalArgumentException.class,()->NativeRiftPlan.create(destination(),Set.of(arrival,a),Map.of(a,b),Set.of(),false));
        assertThrows(IllegalArgumentException.class,()->NativeRiftPlan.create(destination(),Set.of(arrival,a),Map.of(a,a),Set.of(),false));
        assertThrows(IllegalArgumentException.class,()->NativeRiftPlan.create(destination(),Set.of(a,b),Map.of(a,b),Set.of(),false));
    }
    @Test void oneWayMechanismAndInactiveDestinationArePreserved() {
        var plan=NativeRiftPlan.create(destination(),Set.of(arrival,a,b),Map.of(a,b),Set.of(b),false);
        assertEquals(Map.of(a,b),plan.internalLinks());assertEquals(Set.of(arrival),plan.exits());
    }
    @Test void goldStillReturnsEveryOriginalRiftToOverworld() {
        var plan=NativeRiftPlan.create(destination(),Set.of(arrival,a,b),Map.of(a,b,b,a),Set.of(b),true);
        assertTrue(plan.internalLinks().isEmpty());assertEquals(Set.of(arrival,a,b),plan.exits());
    }
    @Test void arrivalRemainsAnExitAndOtherDimensionsNeverCountAsInternal() {
        var foreign=new PortalRecord.Site("dimdoors:private_pockets",b.x(),b.y(),b.z());
        var plan=NativeRiftPlan.create(destination(),Set.of(arrival,a,b),Map.of(arrival,b,a,foreign),Set.of(),false);
        assertEquals(Set.of(arrival,a,b),plan.exits());assertTrue(plan.internalLinks().isEmpty());
    }
    @Test void internalDevicesAreProtectedWithoutProtectingOrdinaryBlocks() {
        var p=new PortalRecord(UUID.randomUUID(),PortalKind.EXPLORATION,3,new PortalRecord.Site("minecraft:overworld",0,70,0),4);
        p.reserve("dimdoors:dungeon/test",destination());p.registerExit(arrival);p.registerInternalLink(a,b);
        assertTrue(p.isInternalDevice(a));assertTrue(p.isInternalDevice(b));assertFalse(p.isInternalDevice(chain));
        assertThrows(IllegalArgumentException.class,()->p.registerExit(a));
        assertThrows(IllegalArgumentException.class,()->p.registerInternalLink(arrival,b));
        assertThrows(IllegalArgumentException.class,()->p.registerInternalLink(a,b));
        assertThrows(IllegalArgumentException.class,()->p.registerInternalLink(b,p(200,3,200)));
    }
}
