package dev.expedition.bridge.domain;

/** Geometry is planned before allocating any native grid cell; dimensions are inclusive. */
public record ReservationGeometry(BlockBounds content, BlockBounds padded, int minBuildY) {
    public static ReservationGeometry plan(BlockBounds content, int margin, int minY, int maxYExclusive) {
        if (minY >= maxYExclusive) throw new IllegalArgumentException("Invalid build height");
        var padded = content.expand(margin);
        if (padded.height() > (long) maxYExclusive - minY)
            throw new IllegalArgumentException("Full structure and margin exceed pocket height; refusing to crop");
        return new ReservationGeometry(content, padded, minY);
    }
    public BlockBounds translatedContent(int originX, int originZ) {
        return content.move(Math.subtractExact(originX, padded.minX()), Math.subtractExact(minBuildY, padded.minY()),
                Math.subtractExact(originZ, padded.minZ()));
    }
}
