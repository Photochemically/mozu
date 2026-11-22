package dev.expedition.bridge.portal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransitNoticeTest {
    @Test void repeatingCollisionOnlyShowsSameNoticeOncePerTwoSeconds() {
        var gate=new TransitNotice.Gate();assertTrue(gate.allow("waiting",100));
        for(long tick=100;tick<140;tick++)assertFalse(gate.allow("waiting",tick));
        assertTrue(gate.allow("waiting",140));
    }
    @Test void changedReasonAndDifferentPlayersAreNotHidden() {
        var a=new TransitNotice.Gate();var b=new TransitNotice.Gate();
        assertTrue(a.allow("waiting",100));assertTrue(b.allow("waiting",100));assertTrue(a.allow("unsafe landing",100));
        assertFalse(a.allow("unsafe landing",101));
    }
    @Test void clockRollbackDoesNotMuteFutureNotices() {
        var gate=new TransitNotice.Gate();assertTrue(gate.allow("waiting",1000));
        assertTrue(gate.allow("waiting",0));assertFalse(gate.allow("waiting",1));
    }
}
