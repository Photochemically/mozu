package dev.expedition.bridge.portal;

import dev.expedition.bridge.domain.BlockBounds;
import net.minecraft.core.*;
import java.util.*;
import java.util.function.Predicate;

/** Keep the original entrance behind the visitor and reserve a clear step forward. No terrain edits. */
public final class EntryApproach {
    public record Choice(BlockPos feet,Direction forward) {}
    private EntryApproach() {}
    public static Optional<Choice> find(BlockPos door,BlockBounds bounds,Predicate<BlockPos> clear) {
        for(int distance:new int[]{2,1})for(var facing:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) {
            boolean usable=true;
            for(int step=1;step<=distance+1;step++) {
                var p=door.relative(facing,step);
                if(!bounds.contains(p.getX(),p.getY(),p.getZ()) || !clear.test(p)){usable=false;break;}
            }
            if(usable)return Optional.of(new Choice(door.relative(facing,distance),facing));
        }
        return Optional.empty();
    }
}
