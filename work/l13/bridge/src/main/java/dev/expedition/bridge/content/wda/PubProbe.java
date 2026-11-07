package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.*;

record PubProbe(ServerLevel world,InteriorBlockProbe floors) implements PubRoute.Probe {
    PubProbe(ServerLevel world) { this(world,new InteriorBlockProbe(world)); }
    public Type at(Cell p) { return floors.at(p); }
    private static BlockPos pos(Cell p) { return new BlockPos(p.x(),p.y(),p.z()); }
    public boolean stair(Cell p,String kind,Direction facing,String shape) {
        var s=world.getBlockState(pos(p));
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString().equals("minecraft:"+kind)
                && s.getValue(StairBlock.FACING)==facing && s.getValue(StairBlock.SHAPE).getSerializedName().equals(shape)
                && s.getValue(StairBlock.HALF)==Half.BOTTOM && s.getFluidState().isEmpty();
    }
    public boolean slab(Cell p) {
        var s=world.getBlockState(pos(p));
        return s.is(Blocks.DARK_OAK_SLAB) && s.getValue(SlabBlock.TYPE)==SlabType.BOTTOM && s.getFluidState().isEmpty();
    }
    public boolean loot(PubLayout.Loot expected) {
        var pos=expected.position();
        return BuiltInRegistries.BLOCK.getKey(world.getBlockState(pos).getBlock()).toString().equals(expected.kind())
                && world.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container
                && container.saveWithoutMetadata().getString("LootTable").equals(expected.table());
    }
}
