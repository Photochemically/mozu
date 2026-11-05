package dev.expedition.bridge.domain;

/** ChunkSerializer rejects references farther than eight chunks. Replicate metadata, never physical pieces. */
public record StructureIndexGrid(int minX,int minZ,int maxX,int maxZ) {
    public record Anchor(int x,int z) {}
    public StructureIndexGrid {
        if(minX>maxX || minZ>maxZ)throw new IllegalArgumentException("Inverted index range");
        Math.toIntExact((long)maxX-minX+1);Math.toIntExact((long)maxZ-minZ+1);
    }
    public Anchor anchorFor(int x,int z) {
        if(x<minX || x>maxX || z<minZ || z>maxZ)throw new IllegalArgumentException("Chunk outside structure index");
        return new Anchor(axis(x,minX,maxX),axis(z,minZ,maxZ));
    }
    private static int axis(int value,int min,int max) {
        int start=Math.toIntExact(min+((long)value-min)/16*16);
        return Math.toIntExact(start+Math.min(15L,(long)max-start)/2);
    }
}
