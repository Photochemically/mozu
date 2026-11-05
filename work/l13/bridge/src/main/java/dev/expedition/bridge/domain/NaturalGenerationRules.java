package dev.expedition.bridge.domain;

import java.util.Set;

/** Natural starts only: the WDA provider's direct Structure.generate call never passes through this policy. */
public final class NaturalGenerationRules {
    private static final Set<String> DD_GATEWAYS=Set.of("enclosed_gateway","enclosed_endstone_gateway","enclosed_mud_gateway",
            "enclosed_prismarine_gateway","enclosed_quartz_gateway","enclosed_red_sandstone_gateway","enclosed_sandstone_gateway");
    private NaturalGenerationRules(){}
    public static boolean suppress(boolean enabled,String dimension,String namespace,String path) {
        return enabled && dimension.equals("minecraft:overworld")
                && (namespace.equals("dungeons_arise") || (namespace.equals("dimdoors") && DD_GATEWAYS.contains(path)));
    }
}
