package dev.expedition.bridge.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeReservationTest {
    private static final String DIM="dimdoors:dungeon_pockets";
    private PortalRecord record(int id,BlockBounds b,PortalRecord.Phase phase) {
        return new PortalRecord(UUID.randomUUID(),PortalKind.GOLD,0,new PortalRecord.Site("minecraft:overworld",0,70,0),1,
                phase,new PortalRecord.Destination(DIM,id,b,new PortalRecord.Site(DIM,b.minX(),b.minY(),b.minZ())),
                "dimdoors:dungeon/upwards_t_treasure",new VisitLedger(),Set.of(),"");
    }
    @Test void fullShellIsReservedBeforeNativeModifierExpandsPocket() {
        var initial=new BlockBounds(101,1,201,131,31,231);
        assertEquals(new BlockBounds(100,0,200,131,31,231),NativeReservation.envelope(initial,32,32,32));
        assertEquals(new BlockBounds(101,1,201,130,30,230),NativeReservation.envelope(initial,30,30,30));
        assertEquals(new BlockBounds(101,1,201,130,30,230),NativeReservation.schematicBounds(initial));
        assertThrows(IllegalArgumentException.class,()->NativeReservation.envelope(initial,31,32,32));
        assertThrows(IllegalArgumentException.class,()->NativeReservation.envelope(initial,28,30,30));
    }
    @Test void failedAndClosedReservationsStillBlockIntersectionAndReusedIds() {
        var bounds=new BlockBounds(100,0,200,132,32,232);
        for(var phase:List.of(PortalRecord.Phase.FAILED,PortalRecord.Phase.CLOSED,PortalRecord.Phase.READY,PortalRecord.Phase.RESERVED)) {
            var records=List.of(record(9,bounds,phase));
            assertThrows(IllegalStateException.class,()->NativeReservation.validate(DIM,10,new BlockBounds(132,0,232,140,20,240),0,256,records));
            assertThrows(IllegalStateException.class,()->NativeReservation.validate(DIM,9,new BlockBounds(500,0,500,510,20,510),0,256,records));
            assertDoesNotThrow(()->NativeReservation.validate(DIM,10,new BlockBounds(133,0,233,140,20,240),0,256,records));
        }
    }
    @Test void heightChecksIncludeOuterShellAndNeverUseLivingDimensions() {
        var shell=NativeReservation.envelope(new BlockBounds(1,1,1,31,255,31),32,256,32);
        assertDoesNotThrow(()->NativeReservation.validate(DIM,1,shell,0,256,List.of()));
        assertThrows(IllegalStateException.class,()->NativeReservation.validate(DIM,1,shell.move(0,1,0),0,256,List.of()));
        assertThrows(IllegalStateException.class,()->NativeReservation.validate(DIM,1,shell.move(0,-1,0),0,256,List.of()));
        assertThrows(IllegalArgumentException.class,()->NativeReservation.validate("dimdoors:private_pockets",1,shell,0,256,List.of()));
    }
    @Test void minecraftInflationReturnsANewBoxSoDiscardingItLosesTheShell() {
        var box=new net.minecraft.world.level.levelgen.structure.BoundingBox(1,1,1,30,30,30);
        var inflated=box.inflatedBy(1);
        assertNotSame(box,inflated);assertEquals(1,box.minX());assertEquals(30,box.maxY());
        assertEquals(0,inflated.minX());assertEquals(31,inflated.maxY());
        assertEquals(new BlockBounds(inflated.minX(),inflated.minY(),inflated.minZ(),inflated.maxX(),inflated.maxY(),inflated.maxZ()),
                NativeReservation.envelope(new BlockBounds(1,1,1,31,31,31),32,32,32));
    }
    @Test void resolvingRiftCanOnlyChangeArrivalBeforeGenerationStarts() {
        var b=new BlockBounds(100,0,200,130,30,230);var p=record(9,b,PortalRecord.Phase.RESERVED);
        var resolved=new PortalRecord.Destination(DIM,9,b,new PortalRecord.Site(DIM,110,5,210));
        p.resolveReservedArrival(resolved);assertEquals(resolved,p.destination());
        assertThrows(IllegalArgumentException.class,()->p.resolveReservedArrival(new PortalRecord.Destination(DIM,10,b,resolved.arrival())));
        assertThrows(IllegalArgumentException.class,()->p.resolveReservedArrival(new PortalRecord.Destination(DIM,9,b.expand(1),resolved.arrival())));
        p.generationStarted();assertThrows(IllegalStateException.class,()->p.resolveReservedArrival(resolved));
    }
}
