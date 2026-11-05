package dev.expedition.bridge.domain;

/** Builder stores max-min, but PocketTemplate subsequently setSize()s to inclusive schematic spans. */
public final class NativeReservation {
    private NativeReservation() {}
    public static BlockBounds envelope(BlockBounds initial, int expectedX, int expectedY, int expectedZ) {
        int x=padding(expectedX,initial.width()), y=padding(expectedY,initial.height()), z=padding(expectedZ,initial.depth());
        return new BlockBounds(Math.subtractExact(initial.minX(),x),Math.subtractExact(initial.minY(),y),Math.subtractExact(initial.minZ(),z),
                Math.addExact(Math.subtractExact(initial.maxX(),1),x),Math.addExact(Math.subtractExact(initial.maxY(),1),y),Math.addExact(Math.subtractExact(initial.maxZ(),1),z));
    }
    public static BlockBounds schematicBounds(BlockBounds initial) {
        return new BlockBounds(initial.minX(),initial.minY(),initial.minZ(),Math.subtractExact(initial.maxX(),1),
                Math.subtractExact(initial.maxY(),1),Math.subtractExact(initial.maxZ(),1));
    }
    private static int padding(int expected,int span) {
        int difference=Math.subtractExact(expected,span-1);
        if(difference<0 || difference%2!=0)throw new IllegalArgumentException("Unreviewed native builder extent");
        return difference/2;
    }
    public static void validate(String dimension,int pocketId,BlockBounds bounds,int minY,int maxY,Iterable<PortalRecord> records) {
        if(!dimension.equals("dimdoors:dungeon_pockets"))throw new IllegalArgumentException("Native exploration cannot allocate living space");
        if(bounds.minY()<minY || bounds.maxY()>=maxY)throw new IllegalStateException("Native room including shell exceeds build height");
        for(var record:records) {
            var previous=record.destination();
            if(previous!=null && previous.dimension().equals(dimension)
                    && (previous.nativePocketId()==pocketId || previous.bounds().intersects(bounds)))
                throw new IllegalStateException("Native allocation conflicts with journaled instance "+record.id()+"; no template blocks written");
        }
    }
}
