package dev.expedition.bridge.domain;

import java.util.Iterator;
import java.util.NoSuchElementException;

/** Separate solid/fluid passes ensure every basin boundary exists before filling water. No giant block list. */
public final class IslandCursor implements Iterator<IslandCursor.Cell> {
    public record Cell(int x,int y,int z) {}
    private final IslandPlan plan;
    private final boolean fluids;
    private final int minChunkX,maxChunkX,maxChunkZ;
    private int chunkX,chunkZ,column=256,x,y,z,top;
    private boolean active;
    public IslandCursor(IslandPlan plan) {
        this(plan,false);
    }
    public IslandCursor(IslandPlan plan,boolean fluids) {
        this.plan=plan;this.fluids=fluids;var b=plan.terrainBounds();
        minChunkX=Math.floorDiv(b.minX(),16);maxChunkX=Math.floorDiv(b.maxX(),16);
        maxChunkZ=Math.floorDiv(b.maxZ(),16);chunkX=minChunkX-1;chunkZ=Math.floorDiv(b.minZ(),16);
    }
    @Override public boolean hasNext() {
        if(!plan.hasTerrain())return false;
        if(fluids && plan.waterDepth()==0)return false;
        if(active && y<=top)return true;
        active=false;
        while(true) {
            if(column==256) {
                if(++chunkX>maxChunkX){chunkX=minChunkX;chunkZ++;}
                if(chunkZ>maxChunkZ)return false;
                column=0;
            }
            x=chunkX*16+(column&15);z=chunkZ*16+(column>>>4);column++;
            var c=fluids?plan.fluidColumn(x,z):plan.solidColumn(x,z);
            if(c!=null){y=c.bottom();top=c.top();active=true;return true;}
        }
    }
    @Override public Cell next() {
        if(!hasNext())throw new NoSuchElementException();
        return new Cell(x,y++,z);
    }
}
