package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.DungeonSpec;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import java.util.*;
import java.util.function.BooleanSupplier;

record WishingWellLayout(BlockPos origin, Rotation rotation, Effect effect, List<BlockPos> sourceOffsets) implements PerimeterLayout {
    static final String TARGET = "dungeons_arise:wishing_well";
    static final String PREFIX = TARGET + "/wishing_well_";
    static final String POOL = "dungeons_arise:prairie/wishing_well/wishing_well_main";

    enum Effect {
        NONE("","",0,0), SPEED("effect_0_speed","speed",1,3200), REGEN("effect_1_regen","regeneration",0,3200),
        WITHER("effect_2_wither","wither",0,200), RESISTANCE("effect_3_resistance","resistance",0,3200),
        HASTE("effect_4_haste","haste",0,3200);

        final String piece, status;
        final int amplifier, ticks;
        Effect(String piece,String status,int amplifier,int ticks) {
            this.piece=piece; this.status=status; this.amplifier=amplifier; this.ticks=ticks;
        }
        static Effect fromPiece(String id) {
            for (var effect : values()) if (effect != NONE && id.equals(PREFIX + effect.piece)) return effect;
            throw new IllegalArgumentException("Unknown wishing well effect piece " + id);
        }
        boolean matches(CompoundTag cloud,String actualStatus) {
            var effects = cloud.getList("Effects",Tag.TAG_COMPOUND);
            if (this == NONE || !actualStatus.equals("minecraft:"+status) || effects.size() != 1) return false;
            var entry = effects.getCompound(0);
            return entry.getInt("Duration") == ticks && entry.getByte("Amplifier") == amplifier
                    && cloud.getFloat("Radius") == 3 && cloud.getFloat("RadiusPerTick") == 0 && cloud.getFloat("RadiusOnUse") == 0;
        }
    }

    static List<BlockPos> sources(MinecraftServer server,String rootId) {
        var template = server.getStructureManager().get(ResourceLocation.parse(rootId)).orElseThrow();
        return template.filterBlocks(BlockPos.ZERO,new StructurePlaceSettings(),Blocks.WATER,true).stream()
                .filter(b -> b.state().getFluidState().isSource()).map(b -> b.pos()).toList();
    }

    static WishingWellLayout inspect(String startPool,List<WdaTemplatePiece> pieces,WdaStructurePlanner.Environment env,List<BlockPos> water) {
        if ((env.surface() != IslandPlan.Surface.GRASS && env.surface() != IslandPlan.Surface.ROCK)
                || env.entryMode() != DungeonSpec.EntryMode.PERIMETER || env.boarding()) {
            throw new IllegalArgumentException("Wishing well needs a dry perimeter");
        }
        boolean plain = startPool.equals(POOL + "_no_effects");
        if ((!plain && !startPool.equals(POOL)) || pieces.size() != (plain ? 1 : 2)
                || pieces.stream().anyMatch(WdaTemplatePiece::terrainMatching)) {
            throw new IllegalArgumentException("Incomplete or unexpected wishing well assembly for " + startPool);
        }
        var root = pieces.get(0);
        if (!root.id().equals(PREFIX + (plain ? "0_no_effects" : "0")) || root.origin().getY() != env.firstFreeY()-8) {
            throw new IllegalArgumentException("Unexpected wishing well root or surface height");
        }
        root.requireFrame(root.origin(),root.rotation(),16,25,16);
        if (water.size() != 19 || Set.copyOf(water).size() != 19 || water.stream().anyMatch(p -> p.getY() != 1
                || p.getX()<4 || p.getX()>10 || p.getZ()<4 || p.getZ()>10)) {
            throw new IllegalArgumentException("Changed original wishing well basin");
        }
        var effect = Effect.NONE;
        if (!plain) {
            var child = pieces.get(1);
            effect = Effect.fromPiece(child.id());
            // The up/down joint is rollable. The child inlet at (2,0,2) must sit above the root's outlet at (7,1,7).
            var inlet = new BlockPos(7,2,7).rotate(root.rotation()).offset(root.origin());
            child.requireFrame(inlet.subtract(new BlockPos(2,0,2).rotate(child.rotation())),child.rotation(),5,2,5);
        }
        return new WishingWellLayout(root.origin(),root.rotation(),effect,List.copyOf(water));
    }

    record Cloud(BlockPos position,double top,boolean emptyPotion,String status,CompoundTag data) {}

    boolean matchesClouds(List<Cloud> clouds) {
        if (effect==Effect.NONE) return clouds.isEmpty();
        if (clouds.size()!=1) return false;
        var cloud = clouds.get(0);
        return cloud.position().equals(pos(7,3,7)) && cloud.top() < origin.getY()+8 && cloud.emptyPotion()
                && effect.matches(cloud.data(),cloud.status());
    }

    AABB bounds() {
        return new AABB(pos(0,0,0)).minmax(new AABB(pos(15,24,15)));
    }

    BlockPos pos(int x,int y,int z) { return new BlockPos(x,y,z).rotate(rotation).offset(origin); }
    Cell at(int x,int y,int z) {
        var p = pos(x,y,z);
        return new Cell(p.getX(),p.getY(),p.getZ());
    }
    @Override public WishingWellLayout move(int x,int y,int z) {
        return new WishingWellLayout(origin.offset(x,y,z),rotation,effect,sourceOffsets);
    }
    @Override public BooleanSupplier survey(ServerLevel world,IslandPlan island,int exits) {
        return new WishingWellRoute(this,island,exits,new WishingWellProbe(world))::step;
    }
}
