package dev.expedition.bridge.portal;

import net.minecraft.core.BlockPos;
import java.util.Objects;
import java.util.function.Supplier;

/** One synchronous write, not a blanket exemption for its neighbor callbacks. */
final class ExactBlockWrite {
    private record Write(Object level,BlockPos pos,Object before,Object after) {}
    private static final ThreadLocal<Write> CURRENT=new ThreadLocal<>();
    private ExactBlockWrite() {}

    static <T> T run(Object level,BlockPos pos,Object before,Object after,Supplier<T> action) {
        var previous=CURRENT.get();
        CURRENT.set(new Write(Objects.requireNonNull(level),pos.immutable(),
                Objects.requireNonNull(before),Objects.requireNonNull(after)));
        try { return action.get(); }
        finally { if(previous==null)CURRENT.remove();else CURRENT.set(previous); }
    }
    static boolean consume(Object level,BlockPos pos,Object before,Object after) {
        var write=CURRENT.get();
        if(write==null || write.level()!=level || !write.pos().equals(pos)
                || write.before()!=before || write.after()!=after)return false;
        CURRENT.remove();return true;
    }
}
