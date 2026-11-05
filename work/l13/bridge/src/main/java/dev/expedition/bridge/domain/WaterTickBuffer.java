package dev.expedition.bridge.domain;

import java.util.*;
import java.util.function.Consumer;

/** Construction-only water ticks: deduplicated until the original hull is complete, then released with a budget. */
public final class WaterTickBuffer {
    public record Cell(int x,int y,int z){}
    private final BlockBounds bounds;
    private final Set<Cell> pending=new LinkedHashSet<>();
    private boolean releasing;
    public WaterTickBuffer(BlockBounds bounds){this.bounds=Objects.requireNonNull(bounds);}
    public boolean defer(int x,int y,int z) {
        if(releasing || !bounds.contains(x,y,z))return false;
        pending.add(new Cell(x,y,z));return true;
    }
    public int remaining(){return pending.size();}
    /** Stop intercepting before rescheduling anything, so resumed water never queues itself again. */
    public void beginRelease(){releasing=true;}
    public int release(int limit,Consumer<Cell> schedule) {
        if(!releasing || limit<=0)throw new IllegalStateException("Invalid water release stage/budget");
        int n=0;var iterator=pending.iterator();
        while(n<limit && iterator.hasNext()) {var cell=iterator.next();schedule.accept(cell);iterator.remove();n++;}
        return n;
    }
}
