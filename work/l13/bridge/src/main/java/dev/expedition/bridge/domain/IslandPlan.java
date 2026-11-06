package dev.expedition.bridge.domain;

/** A flat supported core with a tapering rim. Bounds include both terrain and the complete structure. */
public record IslandPlan(BlockBounds building, int firstFreeY, int padding, int rim, int foundationDepth,
                         Surface surface,int waterDepth) {
    public enum Surface { GRASS, ROCK, WATER, VOID }
    public enum Material { STONE, DIRT, GRASS, WATER }
    public record Column(int bottom, int top) {}

    public IslandPlan(BlockBounds building,int firstFreeY,int padding,int rim,int foundationDepth,Surface surface) {
        this(building,firstFreeY,padding,rim,foundationDepth,surface,0);
    }

    public IslandPlan {
        if (padding < 4 || rim < 1 || foundationDepth < 4 || surface == null)
            throw new IllegalArgumentException("Island requires entry clearance, rim and a solid foundation");
        var levels=new TerrainLevels(firstFreeY,waterDepth);
        if((surface==Surface.WATER)!=(waterDepth>0))throw new IllegalArgumentException("Water islands require explicit depth; dry islands require zero depth");
        // Check arithmetic now, before a native reservation or any block writes.
        building.expand(Math.addExact(padding, rim));
        Math.subtractExact(Math.min(building.minY(), levels.solidFirstFreeY()), foundationDepth);
        Math.addExact(firstFreeY, 2);
    }

    public int coreMinX() { return Math.subtractExact(building.minX(), padding); }
    public int coreMaxX() { return Math.addExact(building.maxX(), padding); }
    public int coreMinZ() { return Math.subtractExact(building.minZ(), padding); }
    public int coreMaxZ() { return Math.addExact(building.maxZ(), padding); }
    public int bottom() { return Math.subtractExact(Math.min(building.minY(), levels().solidFirstFreeY()), foundationDepth); }
    public TerrainLevels levels(){return new TerrainLevels(firstFreeY,waterDepth);}
    /** One block of water beyond the full building footprint; the next three or more blocks form a dry bank. */
    public boolean inBasin(int x,int z) {
        return surface==Surface.WATER && (long)x>=building.minX()-1L && (long)x<=building.maxX()+1L
                && (long)z>=building.minZ()-1L && (long)z<=building.maxZ()+1L;
    }
    public BlockBounds terrainBounds() {
        // Empty terrain has no mathematical box; use the building's scan envelope. hasTerrain() distinguishes it.
        if(!hasTerrain())return building;
        return new BlockBounds(coreMinX()-rim, bottom(), coreMinZ()-rim,
                coreMaxX()+rim, firstFreeY-1, coreMaxZ()+rim);
    }
    public BlockBounds completeBounds() {
        if(!hasTerrain())return building;
        return union(union(building, terrainBounds()),
                new BlockBounds(entryX(), firstFreeY, entryZ(), entryX(), firstFreeY+2, entryZ()));
    }
    public int entryX() { return hasTerrain()?Math.addExact(building.maxX(), 3):building.minX(); }
    public int entryY() { return hasTerrain()?firstFreeY:building.minY(); }
    /** Ordered east, west, north and south to match existing saved perimeter destinations. */
    public java.util.List<BoardingRouteSearch.Cell> perimeterSites(int count) {
        if (!hasTerrain() || count < 1 || count > 4) throw new IllegalArgumentException("Invalid perimeter exit count or environment");
        int midX = building.minX() + (building.width() - 1) / 2;
        return java.util.List.of(new BoardingRouteSearch.Cell(entryX(), firstFreeY, entryZ()),
                new BoardingRouteSearch.Cell(building.minX() - 3, firstFreeY, entryZ()),
                new BoardingRouteSearch.Cell(midX, firstFreeY, building.minZ() - 3),
                new BoardingRouteSearch.Cell(midX, firstFreeY, building.maxZ() + 3)).subList(0, count);
    }
    public int entryZ() { return hasTerrain()?building.minZ()+(building.depth()-1)/2:building.minZ(); }
    public boolean hasTerrain(){return surface!=Surface.VOID;}

    /** Null outside the rounded rim; the entire structure footprint has the same original planning height. */
    public Column column(int x, int z) {
        if(!hasTerrain())return null;
        long dx=Math.max(0L, Math.max((long)coreMinX()-x, (long)x-coreMaxX()));
        long dz=Math.max(0L, Math.max((long)coreMinZ()-z, (long)z-coreMaxZ()));
        double distance=Math.hypot(dx,dz);
        if(distance>rim)return null;
        int thickness=Math.toIntExact((long)firstFreeY-bottom());
        int remaining=Math.max(1,(int)Math.ceil(thickness*(1.0-distance/(rim+1.0))));
        return new Column(firstFreeY-remaining,firstFreeY-1);
    }
    public Column solidColumn(int x,int z) {
        var full=column(x,z);return full==null?null:inBasin(x,z)?new Column(full.bottom(),levels().solidFirstFreeY()-1):full;
    }
    public Column fluidColumn(int x,int z) {
        return inBasin(x,z)?new Column(levels().solidFirstFreeY(),firstFreeY-1):null;
    }
    public Material material(int x,int y,int z) {
        var c=column(x,z);
        if(c==null || y<c.bottom() || y>c.top())throw new IllegalArgumentException("Terrain cell outside island");
        if(inBasin(x,z) && y>=levels().solidFirstFreeY())return Material.WATER;
        if(surface==Surface.GRASS) {
            if(y==firstFreeY-1)return Material.GRASS;
            if(y>=firstFreeY-3)return Material.DIRT;
        }
        return Material.STONE;
    }
    public IslandPlan move(int x,int y,int z) {
        return new IslandPlan(building.move(x,y,z),Math.addExact(firstFreeY,y),padding,rim,foundationDepth,surface,waterDepth);
    }
    public static BlockBounds union(BlockBounds a, BlockBounds b) {
        return new BlockBounds(Math.min(a.minX(),b.minX()),Math.min(a.minY(),b.minY()),Math.min(a.minZ(),b.minZ()),
                Math.max(a.maxX(),b.maxX()),Math.max(a.maxY(),b.maxY()),Math.max(a.maxZ(),b.maxZ()));
    }
}
