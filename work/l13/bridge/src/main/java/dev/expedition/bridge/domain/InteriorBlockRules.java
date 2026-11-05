package dev.expedition.bridge.domain;

import java.util.Set;

/** No machinery, hazards, liquids or block entities are accepted as door supports. Rails can be walked past, not replaced. */
public final class InteriorBlockRules {
    public enum Type { AIR, PASSABLE, FLOOR_DECOR, SUPPORT, BLOCKED }
    private static final Set<String> STONE=Set.of("andesite","polished_andesite","granite","polished_granite","diorite","polished_diorite",
            "obsidian","nether_bricks","bricks","blackstone","polished_blackstone","polished_blackstone_bricks",
            "cracked_polished_blackstone_bricks","cracked_stone_bricks","mossy_stone_bricks","mossy_cobblestone",
            "deepslate","cobbled_deepslate","polished_deepslate","deepslate_bricks","deepslate_tiles","tuff","grass_block","coarse_dirt","dirt",
            "mud_bricks","packed_mud","dark_prismarine","prismarine","prismarine_bricks","clay","end_stone","end_stone_bricks",
            "sandstone","cut_sandstone","smooth_sandstone","red_sandstone","cut_red_sandstone","smooth_red_sandstone","snow_block","shroomlight",
            "copper_block","exposed_copper","weathered_copper","oxidized_copper","waxed_copper_block","waxed_exposed_copper","waxed_weathered_copper","waxed_oxidized_copper",
            "cut_copper","exposed_cut_copper","weathered_cut_copper","oxidized_cut_copper","waxed_cut_copper","waxed_exposed_cut_copper","waxed_weathered_cut_copper","waxed_oxidized_cut_copper");
    private InteriorBlockRules(){}
    public static Type classify(String id,String slab,boolean wet,boolean blockEntity) {
        return classify(id,slab,"",wet,blockEntity);
    }
    public static Type classify(String id,String slab,String stairHalf,boolean wet,boolean blockEntity) {
        if(blockEntity || wet)return Type.BLOCKED;
        var common=BoardingBlockRules.classify(id,slab,false,false);
        if(common==BoardingRouteSearch.Block.AIR)return Type.AIR;
        if(common==BoardingRouteSearch.Block.SUPPORT)return Type.SUPPORT;
        if(id.startsWith("minecraft:") && STONE.contains(id.substring(10)))return Type.SUPPORT;
        // Every vanilla upside-down stair shape includes the complete upper half cube. Normal stairs do not.
        if(id.startsWith("minecraft:") && id.endsWith("_stairs") && stairHalf.equals("top"))return Type.SUPPORT;
        if(id.startsWith("minecraft:") && id.endsWith("_carpet"))return Type.FLOOR_DECOR;
        if(Set.of("minecraft:rail","minecraft:powered_rail","minecraft:detector_rail","minecraft:activator_rail").contains(id))return Type.PASSABLE;
        return Type.BLOCKED;
    }
}
