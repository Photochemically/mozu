package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.TerrainLevels;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainHeightmapTest {
    @Test void voidPlanningAndPlacementHeightmapsContainNoSyntheticSurface() {
        var levels=new TerrainLevels(96,0);for(var type:Heightmap.Types.values()) {
            assertEquals(-64,TerrainHeightmap.height(levels,type,true,-64));
            assertEquals(0,TerrainHeightmap.height(levels,type,true,0));
            assertEquals(96,TerrainHeightmap.height(levels,type,false,0));
        }
    }
    @Test void waterAndOceanFloorAreDistinctAndColumnBoundariesAgree() {
        var levels=new TerrainLevels(96,12);
        assertEquals(84,TerrainHeightmap.height(levels,Heightmap.Types.OCEAN_FLOOR));
        assertEquals(84,TerrainHeightmap.height(levels,Heightmap.Types.OCEAN_FLOOR_WG));
        for(var type:new Heightmap.Types[]{Heightmap.Types.WORLD_SURFACE,Heightmap.Types.WORLD_SURFACE_WG,
                Heightmap.Types.MOTION_BLOCKING,Heightmap.Types.MOTION_BLOCKING_NO_LEAVES})assertEquals(96,TerrainHeightmap.height(levels,type));
        assertEquals(TerrainLevels.Layer.SOLID,levels.layer(83));assertEquals(TerrainLevels.Layer.WATER,levels.layer(84));
        assertEquals(TerrainLevels.Layer.WATER,levels.layer(95));assertEquals(TerrainLevels.Layer.AIR,levels.layer(96));
    }
    @Test void dryHeightmapsRetainOneSurfaceIncludingNegativeY() {
        var levels=new TerrainLevels(-16,0);for(var type:Heightmap.Types.values())assertEquals(-16,TerrainHeightmap.height(levels,type));
        assertEquals(TerrainLevels.Layer.SOLID,levels.layer(-17));assertEquals(TerrainLevels.Layer.AIR,levels.layer(-16));
    }
}
