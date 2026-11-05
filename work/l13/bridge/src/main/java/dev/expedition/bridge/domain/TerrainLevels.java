package dev.expedition.bridge.domain;

/** Heights are exclusive: water occupies [solidFirstFreeY, firstFreeY). Zero depth preserves dry terrain. */
public record TerrainLevels(int firstFreeY,int waterDepth) {
    public enum Layer { SOLID, WATER, AIR }
    public TerrainLevels {
        if(waterDepth<0 || waterDepth>64 || waterDepth==1)throw new IllegalArgumentException("Water depth must be 0 or 2..64");
        Math.subtractExact(firstFreeY,waterDepth);
    }
    public int solidFirstFreeY(){return Math.subtractExact(firstFreeY,waterDepth);}
    public int firstFreeY(boolean includeFluids){return includeFluids?firstFreeY:solidFirstFreeY();}
    public Layer layer(int y){return y<solidFirstFreeY()?Layer.SOLID:y<firstFreeY?Layer.WATER:Layer.AIR;}
}
