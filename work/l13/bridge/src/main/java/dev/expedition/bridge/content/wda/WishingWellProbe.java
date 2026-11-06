package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

record WishingWellProbe(ServerLevel world,InteriorBlockProbe floors) implements WishingWellRoute.Probe {
    WishingWellProbe(ServerLevel world) { this(world,new InteriorBlockProbe(world)); }
    private static BlockPos pos(Cell p) { return new BlockPos(p.x(),p.y(),p.z()); }
    public Type at(Cell p) { return floors.at(p); }
    public boolean grass(Cell p) { return world.getBlockState(pos(p)).is(Blocks.GRASS); }
    public boolean rim(Cell p,Direction facing) {
        var state = world.getBlockState(pos(p));
        return state.is(Blocks.STONE_BRICK_STAIRS) && state.getValue(StairBlock.FACING)==facing
                && state.getValue(StairBlock.HALF)==Half.TOP && state.getValue(StairBlock.SHAPE)==StairsShape.STRAIGHT
                && state.getFluidState().isEmpty();
    }
    public boolean source(BlockPos p) {
        var state = world.getBlockState(p);
        return state.is(Blocks.WATER) && state.getFluidState().isSource();
    }
    public boolean fixtures(WishingWellLayout well) {
        var cauldron = world.getBlockState(well.pos(7,11,7));
        var lantern = world.getBlockState(well.pos(7,7,7));
        if (!cauldron.is(Blocks.WATER_CAULDRON) || cauldron.getValue(LayeredCauldronBlock.LEVEL)!=3
                || !lantern.is(Blocks.SOUL_LANTERN) || !lantern.getValue(LanternBlock.HANGING) || !lantern.getFluidState().isEmpty()) return false;
        for (int y=8;y<=9;y++) {
            var chain = world.getBlockState(well.pos(7,y,7));
            if (!chain.is(Blocks.CHAIN) || chain.getValue(ChainBlock.AXIS)!=Direction.Axis.Y || !chain.getFluidState().isEmpty()) return false;
        }
        return well.effect()==WishingWellLayout.Effect.NONE || world.getBlockState(well.pos(7,1,7)).is(Blocks.GRAVEL);
    }
    static WishingWellLayout.Cloud describe(AreaEffectCloud entity) {
        var data = entity.saveWithoutId(new CompoundTag());
        var effects = data.getList("Effects",Tag.TAG_COMPOUND);
        var status = effects.size()==1 ? MobEffectInstance.load(effects.getCompound(0)) : null;
        return new WishingWellLayout.Cloud(entity.blockPosition(),entity.getBoundingBox().maxY,entity.getPotion()==Potions.EMPTY,
                status==null ? "" : BuiltInRegistries.MOB_EFFECT.getKey(status.getEffect()).toString(),data);
    }
}
