package dev.expedition.bridge.domain;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MechanicalBridgeSiteSearchTest {
    private final MechanicalBridgeLayout.Joint gate=new MechanicalBridgeLayout.Joint(new Cell(0,6,0),-1,0,
            MechanicalBridgeLayout.ROOT_NAME,MechanicalBridgeLayout.BRIDGE_NAME,MechanicalBridgeLayout.PREFIX+"mechanical_nest_bridges","minecraft:air");
    private final MechanicalBridgeLayout layout=new MechanicalBridgeLayout(List.of(new MechanicalBridgeLayout.Approach(
            new BlockBounds(-18,0,-6,-1,14,6),gate,new Cell(-1,6,0))));
    private Type deck(Cell p){return p.y()==5?Type.SUPPORT:Type.AIR;}
    private MechanicalBridgeSiteSearch search(MechanicalBridgeLayout l,InteriorSiteSearch.Probe probe,int budget) {
        var s=new MechanicalBridgeSiteSearch(l,probe);int loops=0;
        while(!s.complete() && loops++<200000)s.step(budget);
        assertTrue(s.complete());return s;
    }
    @Test void entryRemainsOnTheConnectedBridgeAtGateHeightWithStableBudgeting() {
        var s=search(layout,this::deck,1);var p=s.result().orElseThrow();
        assertEquals(6,p.y());assertTrue(p.x()<=-2);assertTrue(InteriorSiteSearch.clearPlatform(p,this::deck));
        assertEquals(p,search(layout,this::deck,256).result().orElseThrow());assertEquals(layout.approaches().get(0).bridge(),s.selectedBridge().orElseThrow());
    }
    @Test void disconnectedDeckCannotBeSelectedEvenWhenAnotherPlatformIsLargeAndClear() {
        assertTrue(search(layout,p->p.x()==-2 && p.y()==6?Type.BLOCKED:deck(p),16).result().isEmpty());
    }
    @Test void rootApproachObstructionAndMissingConnectionFailWithoutMovingInside() {
        assertTrue(search(layout,p->p.equals(gate.pos())?Type.BLOCKED:deck(p),16).result().isEmpty());
        assertTrue(search(layout,p->p.equals(new Cell(1,6,0))?Type.BLOCKED:deck(p),16).result().isEmpty());
        assertThrows(IllegalArgumentException.class,()->new MechanicalBridgeLayout.Approach(layout.approaches().get(0).bridge(),gate,new Cell(-2,6,0)));
        assertThrows(IllegalArgumentException.class,()->new MechanicalBridgeLayout.Approach(new BlockBounds(-18,0,-6,1,14,6),gate,new Cell(-1,6,0)));
    }
    @Test void carpetCanBeTraversedButNeverReplacedByAnEntryAndRoofsAreNotFallbacks() {
        var s=search(layout,p->p.y()==6 && p.x()>=-3?Type.FLOOR_DECOR:deck(p),16);
        assertTrue(s.result().orElseThrow().x()<=-4);
        assertTrue(search(layout,p->p.y()==6?Type.FLOOR_DECOR:p.y()==10?Type.SUPPORT:deck(p),16).result().isEmpty());
    }
    @Test void tinyTerminatorAndNarrowBridgeDoNotLowerExistingPlatformStandards() {
        var shortBridge=new MechanicalBridgeLayout(List.of(new MechanicalBridgeLayout.Approach(new BlockBounds(-3,0,-6,-1,14,6),gate,new Cell(-1,6,0))));
        assertTrue(search(shortBridge,this::deck,16).result().isEmpty());
        assertTrue(search(layout,p->p.y()==5 && Math.abs(p.z())>1?Type.AIR:deck(p),16).result().isEmpty());
    }
    @Test void failedOriginalBridgeMayTryAnotherAttachedGateAndTranslationPreservesItsIdentity() {
        var a=layout.approaches().get(0);var other=a.move(100,40,-200);var both=new MechanicalBridgeLayout(List.of(a,other));
        InteriorSiteSearch.Probe world=p->p.x()<50?Type.BLOCKED:deck(p.move(-100,-40,200));
        var selected=search(both,world,16).result().orElseThrow();
        assertEquals(search(layout,this::deck,16).result().orElseThrow().move(100,40,-200),selected);
        assertEquals(new MechanicalBridgeLayout(List.of(other)),layout.move(100,40,-200));
        assertThrows(IllegalArgumentException.class,()->new MechanicalBridgeSiteSearch(layout,this::deck).step(0));
    }
}
