package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.TerrainLevels;
import net.minecraft.world.level.levelgen.Heightmap;

/** Shared by native assembly and placement, so ocean-floor queries never become water-surface queries. */
final class TerrainHeightmap {
    private TerrainHeightmap(){}
    static int height(TerrainLevels levels,Heightmap.Types type) {
        return levels.firstFreeY(type!=Heightmap.Types.OCEAN_FLOOR && type!=Heightmap.Types.OCEAN_FLOOR_WG);
    }
    static int height(TerrainLevels levels,Heightmap.Types type,boolean voidTerrain,int minY) {
        return voidTerrain?minY:height(levels,type);
    }
}
