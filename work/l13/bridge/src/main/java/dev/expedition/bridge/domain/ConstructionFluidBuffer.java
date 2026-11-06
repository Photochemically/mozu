package dev.expedition.bridge.domain;

import java.util.*;
import java.util.function.Consumer;

/** Construction-only selected fluid ticks: deduplicated by cell until all native pieces are complete. */
public final class ConstructionFluidBuffer {
    public enum Kind { WATER, LAVA, OTHER }
    public record Cell(int x,int y,int z){}
    private final BlockBounds bounds;
    private final Set<Cell> pending=new LinkedHashSet<>();
    private boolean releasing;
    private final boolean water,lava;
    public ConstructionFluidBuffer(BlockBounds bounds,boolean water,boolean lava){this.bounds=Objects.requireNonNull(bounds);this.water=water;this.lava=lava;}
    public boolean manages(Kind kind){return kind==Kind.WATER && water || kind==Kind.LAVA && lava;}
    public boolean defer(int x,int y,int z,Kind kind) {
        if(releasing || !manages(kind) || !bounds.contains(x,y,z))return false;
        pending.add(new Cell(x,y,z));return true;
    }
    public int remaining(){return pending.size();}
    /** Stop intercepting before rescheduling; inspect the CURRENT fluid at release, never resurrect a removed one. */
    public void beginRelease(){releasing=true;}
    public int release(int limit,Consumer<Cell> schedule) {
        if(!releasing || limit<=0)throw new IllegalStateException("Invalid fluid release stage/budget");
        int n=0;var iterator=pending.iterator();
        while(n<limit && iterator.hasNext()) {var cell=iterator.next();schedule.accept(cell);iterator.remove();n++;}
        return n;
    }
}
