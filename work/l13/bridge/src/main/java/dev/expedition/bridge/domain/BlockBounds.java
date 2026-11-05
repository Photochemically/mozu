package dev.expedition.bridge.domain;

/** Inclusive integer bounds, independent of Minecraft initialization. */
public record BlockBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public BlockBounds {
        if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("Inverted bounds");
        Math.toIntExact((long) maxX - minX + 1);
        Math.toIntExact((long) maxY - minY + 1);
        Math.toIntExact((long) maxZ - minZ + 1);
    }
    public int width() { return Math.toIntExact((long) maxX - minX + 1); }
    public int height() { return Math.toIntExact((long) maxY - minY + 1); }
    public int depth() { return Math.toIntExact((long) maxZ - minZ + 1); }
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
    public boolean contains(BlockBounds other) {
        return contains(other.minX, other.minY, other.minZ) && contains(other.maxX, other.maxY, other.maxZ);
    }
    public boolean intersects(BlockBounds other) {
        return minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }
    public BlockBounds move(int x, int y, int z) {
        return new BlockBounds(Math.addExact(minX,x),Math.addExact(minY,y),Math.addExact(minZ,z),
                Math.addExact(maxX,x),Math.addExact(maxY,y),Math.addExact(maxZ,z));
    }
    public BlockBounds expand(int margin) {
        if (margin < 0) throw new IllegalArgumentException("Negative margin");
        return new BlockBounds(Math.subtractExact(minX,margin),Math.subtractExact(minY,margin),Math.subtractExact(minZ,margin),
                Math.addExact(maxX,margin),Math.addExact(maxY,margin),Math.addExact(maxZ,margin));
    }
}
