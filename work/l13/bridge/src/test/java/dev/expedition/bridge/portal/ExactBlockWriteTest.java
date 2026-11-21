package dev.expedition.bridge.portal;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ExactBlockWriteTest {
    @Test void grantMatchesLevelPositionAndBothStatesAndIsConsumedBeforeCallbacks() {
        var level=new Object();var before=new Object();var after=new Object();var pos=new BlockPos(1,2,3);
        ExactBlockWrite.run(level,pos,before,after,()->{
            assertFalse(ExactBlockWrite.consume(new Object(),pos,before,after));
            assertFalse(ExactBlockWrite.consume(level,pos.above(),before,after));
            assertFalse(ExactBlockWrite.consume(level,pos,new Object(),after));
            assertFalse(ExactBlockWrite.consume(level,pos,before,new Object()));
            assertTrue(ExactBlockWrite.consume(level,pos,before,after));
            assertFalse(ExactBlockWrite.consume(level,pos,before,after));return true;
        });
        assertFalse(ExactBlockWrite.consume(level,pos,before,after));
    }
    @Test void exceptionsAndNestedWritesDoNotLeakPermissions() {
        var level=new Object();var before=new Object();var after=new Object();var pos=BlockPos.ZERO;
        ExactBlockWrite.run(level,pos,before,after,()->{
            assertTrue(ExactBlockWrite.consume(level,pos,before,after));
            assertThrows(IllegalStateException.class,()->ExactBlockWrite.run(level,pos.above(),before,after,()->{
                throw new IllegalStateException("failed write");
            }));
            assertFalse(ExactBlockWrite.consume(level,pos.above(),before,after));
            assertFalse(ExactBlockWrite.consume(level,pos,before,after));return false;
        });
        assertThrows(IllegalStateException.class,()->ExactBlockWrite.run(level,pos,before,after,()->{
            throw new IllegalStateException("failed before consuming");
        }));
        assertFalse(ExactBlockWrite.consume(level,pos,before,after));
    }
    @Test void nestedRequestCannotBorrowTheOuterWriteAndRestoresItAfterwards() {
        var level=new Object();var before=new Object();var after=new Object();var pos=BlockPos.ZERO;
        ExactBlockWrite.run(level,pos,before,after,()->{
            ExactBlockWrite.run(level,pos.above(),before,after,()->{
                assertFalse(ExactBlockWrite.consume(level,pos,before,after));
                assertTrue(ExactBlockWrite.consume(level,pos.above(),before,after));return true;
            });
            assertTrue(ExactBlockWrite.consume(level,pos,before,after));return true;
        });
    }
    @Test void mutableCoordinatesAndOtherThreadsCannotBorrowTheGrant() throws Exception {
        var level=new Object();var before=new Object();var after=new Object();var pos=new BlockPos.MutableBlockPos(1,2,3);
        var executor=Executors.newSingleThreadExecutor();
        try {
            ExactBlockWrite.run(level,pos,before,after,()->{
                pos.set(99,99,99);
                try { assertFalse(executor.submit(()->ExactBlockWrite.consume(level,new BlockPos(1,2,3),before,after)).get(5,TimeUnit.SECONDS)); }
                catch(Exception failure){throw new AssertionError(failure);}
                assertFalse(ExactBlockWrite.consume(level,pos,before,after));
                assertTrue(ExactBlockWrite.consume(level,new BlockPos(1,2,3),before,after));return true;
            });
        } finally { executor.shutdownNow(); }
    }
}
