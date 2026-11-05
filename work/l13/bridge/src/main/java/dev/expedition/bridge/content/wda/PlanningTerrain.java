package dev.expedition.bridge.content.wda;

import com.mojang.serialization.Codec;
import dev.expedition.bridge.domain.TerrainLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import java.util.*;
import java.util.concurrent.*;

/** Read-only terrain supplied to the original structure assembler, never installed as a dimension generator. */
final class PlanningTerrain extends ChunkGenerator {
    private final int minY, height;
    private final TerrainLevels levels;
    private final boolean voidTerrain;
    PlanningTerrain(BiomeSource biomes, int firstFreeY, int minY, int height,int waterDepth) {
        this(biomes,firstFreeY,minY,height,waterDepth,false);
    }
    PlanningTerrain(BiomeSource biomes, int firstFreeY, int minY, int height,int waterDepth,boolean voidTerrain) {
        super(biomes);
        levels=new TerrainLevels(firstFreeY,waterDepth);
        if(levels.solidFirstFreeY()<=minY || firstFreeY>=Math.addExact(minY,height))
            throw new IllegalArgumentException("Planning surface outside height range");
        this.minY=minY;this.height=height;this.voidTerrain=voidTerrain;
    }
    @Override protected Codec<? extends ChunkGenerator> codec() { return Codec.unit(this); }
    @Override public int getGenDepth() { return height; }
    @Override public int getMinY() { return minY; }
    @Override public int getSeaLevel() { return !voidTerrain && levels.waterDepth()>0?levels.firstFreeY():minY; }
    @Override public int getBaseHeight(int x,int z,Heightmap.Types type,LevelHeightAccessor accessor,RandomState random) {
        return Math.max(accessor.getMinBuildHeight(),Math.min(TerrainHeightmap.height(levels,type,voidTerrain,accessor.getMinBuildHeight()),accessor.getMaxBuildHeight()));
    }
    @Override public NoiseColumn getBaseColumn(int x,int z,LevelHeightAccessor accessor,RandomState random) {
        BlockState[] states=new BlockState[accessor.getHeight()];
        for(int i=0;i<states.length;i++)states[i]=voidTerrain?Blocks.AIR.defaultBlockState():switch(levels.layer(accessor.getMinBuildHeight()+i)) {
            case SOLID -> Blocks.STONE.defaultBlockState();
            case WATER -> Blocks.WATER.defaultBlockState();
            case AIR -> Blocks.AIR.defaultBlockState();
        };
        return new NoiseColumn(accessor.getMinBuildHeight(),states);
    }
    @Override public void addDebugScreenInfo(List<String> lines,RandomState state,BlockPos pos) { }
    @Override public void applyCarvers(WorldGenRegion region,long seed,RandomState random,BiomeManager biomes,
                                      StructureManager structures,ChunkAccess chunk,GenerationStep.Carving step) { throw readOnly(); }
    @Override public void buildSurface(WorldGenRegion region,StructureManager structures,RandomState random,ChunkAccess chunk) { throw readOnly(); }
    @Override public void spawnOriginalMobs(WorldGenRegion region) { throw readOnly(); }
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor,Blender blender,RandomState random,
                                                                 StructureManager structures,ChunkAccess chunk) { throw readOnly(); }
    private UnsupportedOperationException readOnly() { return new UnsupportedOperationException("Planning terrain cannot generate world chunks"); }
}
