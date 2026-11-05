package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BlockBounds;
import dev.expedition.bridge.domain.TerrainLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.List;

/** Native template placement with a stable pre-building heightmap, even across separate ticks/chunk reloads. */
final class IslandPlacementRegion extends WorldGenRegion {
    private final ServerLevel target;
    private final BlockBounds reservation;
    private final BoundingBox clip;
    private final TerrainLevels levels;
    private final long instanceSeed;
    private final boolean voidTerrain;
    IslandPlacementRegion(ServerLevel target,ChunkAccess center,BlockBounds reservation,BoundingBox clip,TerrainLevels levels,long seed,boolean voidTerrain) {
        super(target,List.of(center),ChunkStatus.FEATURES,0);
        this.target=target;this.reservation=reservation;this.clip=clip;this.levels=levels;this.instanceSeed=seed;this.voidTerrain=voidTerrain;
    }
    @Override public int getHeight(Heightmap.Types type,int x,int z) { return TerrainHeightmap.height(levels,type,voidTerrain,target.getMinBuildHeight()); }
    @Override public int getSeaLevel() { return voidTerrain?target.getMinBuildHeight():levels.waterDepth()>0?levels.firstFreeY():super.getSeaLevel(); }
    @Override public long getSeed() { return instanceSeed; }
    // Rule processors inspect complete templates before chunk clipping. Reads may cross the current chunk.
    @Override public ChunkAccess getChunk(int x,int z,ChunkStatus status,boolean create) {
        return target.getChunkSource().getChunk(x,z,ChunkStatus.FULL,create);
    }
    @Override public boolean hasChunk(int x,int z) { return target.hasChunk(x,z); }
    @Override public boolean ensureCanWrite(BlockPos pos) {
        if(!reservation.contains(pos.getX(),pos.getY(),pos.getZ()))
            throw new IllegalStateException("Native template write escaped reserved space at "+pos);
        return clip.isInside(pos);
    }
    @Override public boolean setBlock(BlockPos pos,BlockState state,int flags,int recursion) {
        return ensureCanWrite(pos) && target.setBlock(pos,state,flags,recursion);
    }
    @Override public boolean removeBlock(BlockPos pos,boolean moving) {
        return ensureCanWrite(pos) && target.removeBlock(pos,moving);
    }
    @Override public boolean destroyBlock(BlockPos pos,boolean drops,Entity entity,int recursion) {
        return ensureCanWrite(pos) && target.destroyBlock(pos,drops,entity,recursion);
    }
    @Override public boolean addFreshEntity(Entity entity) {
        return ensureCanWrite(entity.blockPosition()) && target.addFreshEntity(entity);
    }
    @Override public void addFreshEntityWithPassengers(Entity entity) {
        if(!ensureCanWrite(entity.blockPosition()))throw new IllegalStateException("Template entity outside placement chunk");
        // Passengers may cross a chunk edge with their vehicle; do not silently drop those riders.
        entity.getSelfAndPassengers().forEach(part->{
            var p=part.blockPosition();
            if(!reservation.contains(p.getX(),p.getY(),p.getZ()))
                throw new IllegalStateException("Template passenger escaped reserved space at "+p);
        });
        target.addFreshEntityWithPassengers(entity);
    }
}
