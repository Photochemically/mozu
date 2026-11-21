package dev.expedition.bridge.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.*;

/** A piston can destroy a protected destination without moving a protected source. Check both. */
public final class BlockMutationFootprint {
    private BlockMutationFootprint() {}
    public static Set<BlockPos> piston(BlockPos base,Direction facing,boolean extending,
                                       Collection<BlockPos> moved,Collection<BlockPos> destroyed) {
        return piston(base,facing,extending,moved,destroyed,Set.of());
    }
    public static Set<BlockPos> piston(BlockPos base,Direction facing,boolean extending,
                                       Collection<BlockPos> moved,Collection<BlockPos> destroyed,Collection<BlockPos> completed) {
        var result=new HashSet<BlockPos>();result.add(base.immutable());result.add(base.relative(facing));
        var movement=extending?facing:facing.getOpposite();
        for(var source:moved){result.add(source.immutable());result.add(source.relative(movement));}
        for(var pos:destroyed)result.add(pos.immutable());
        for(var pos:completed)result.add(pos.immutable());
        return Set.copyOf(result);
    }
}
