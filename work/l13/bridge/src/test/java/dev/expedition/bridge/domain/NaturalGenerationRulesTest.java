package dev.expedition.bridge.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NaturalGenerationRulesTest {
    @Test void suppressesWdaAndAllSevenNativeOverworldGatewayStructures() {
        assertTrue(NaturalGenerationRules.suppress(true,"minecraft:overworld","dungeons_arise","keep_kayra"));
        for(String name:new String[]{"enclosed_gateway","enclosed_endstone_gateway","enclosed_mud_gateway","enclosed_prismarine_gateway",
                "enclosed_quartz_gateway","enclosed_red_sandstone_gateway","enclosed_sandstone_gateway"})
            assertTrue(NaturalGenerationRules.suppress(true,"minecraft:overworld","dimdoors",name));
    }
    @Test void preservesOtherDimensionsModsNativeFeaturesAndDisabledMode() {
        for(String dim:new String[]{"minecraft:the_nether","minecraft:the_end","dimdoors:dungeon_pockets","other:overworld"}) {
            assertFalse(NaturalGenerationRules.suppress(true,dim,"dungeons_arise","keep_kayra"));
            assertFalse(NaturalGenerationRules.suppress(true,dim,"dimdoors","enclosed_gateway"));
        }
        assertFalse(NaturalGenerationRules.suppress(true,"minecraft:overworld","minecraft","village_plains"));
        assertFalse(NaturalGenerationRules.suppress(true,"minecraft:overworld","dimdoors","unrelated_structure"));
        assertFalse(NaturalGenerationRules.suppress(false,"minecraft:overworld","dungeons_arise","keep_kayra"));
    }
}
