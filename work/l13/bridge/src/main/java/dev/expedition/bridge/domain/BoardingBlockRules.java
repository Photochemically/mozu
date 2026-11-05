package dev.expedition.bridge.domain;

/** Conservative vanilla deck supports, shared by real-world inspection and the original-NBT audit. */
public final class BoardingBlockRules {
    private BoardingBlockRules(){}
    public static BoardingRouteSearch.Block classify(String id,String slabType,boolean waterlogged,boolean blockEntity) {
        if(blockEntity)return BoardingRouteSearch.Block.BLOCKED;
        if(id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air"))return BoardingRouteSearch.Block.AIR;
        if(id.equals("minecraft:water"))return BoardingRouteSearch.Block.WATER;
        if(waterlogged || !id.startsWith("minecraft:"))return BoardingRouteSearch.Block.BLOCKED;
        String name=id.substring(10);
        if(name.matches("[a-z_]+_(planks|log|wood|hyphae|stem|concrete|terracotta|wool)")
                || name.equals("coal_block") || name.equals("quartz_block") || name.equals("stone")
                || name.equals("cobblestone") || name.equals("stone_bricks") || name.equals("iron_block")
                || name.equals("terracotta") || name.equals("bone_block")
                || name.endsWith("_slab") && (slabType.equals("top") || slabType.equals("double")))
            return BoardingRouteSearch.Block.SUPPORT;
        return BoardingRouteSearch.Block.BLOCKED;
    }
}
