package dev.expedition.bridge.domain;

/** Inclusive 48 by 48 block footprint: the entrance chunk and its eight neighbors. */
public record ReturnArea(int minX,int minZ,int maxX,int maxZ) {
    public static ReturnArea around(PortalRecord.Site entrance) {
        int x=Math.multiplyExact(Math.floorDiv(entrance.x(),16),16);
        int z=Math.multiplyExact(Math.floorDiv(entrance.z(),16),16);
        return new ReturnArea(Math.subtractExact(x,16),Math.subtractExact(z,16),Math.addExact(x,31),Math.addExact(z,31));
    }
    public boolean contains(int x,int z){return x>=minX && x<=maxX && z>=minZ && z<=maxZ;}
}
