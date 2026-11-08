package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.properties.SlabType;

record VillageProbe(ServerLevel world,InteriorBlockProbe floors) implements VillageRoute.Probe {
    VillageProbe(ServerLevel world) {this(world,new InteriorBlockProbe(world));}
    private static BlockPos pos(Cell p) {return new BlockPos(p.x(),p.y(),p.z());}
    public Type at(Cell p) {
        var s=world.getBlockState(pos(p));
        if ((s.is(Blocks.MYCELIUM)||s.is(Blocks.MOSS_BLOCK)) && s.getFluidState().isEmpty() && s.isFaceSturdy(world,pos(p),Direction.UP)) return Type.SUPPORT;
        return floors.at(p);
    }
    public boolean door(Cell p,Direction facing) {return floors.door(p,Blocks.BIRCH_DOOR,facing);}
    public boolean halfSlab(Cell p) {
        var s=world.getBlockState(pos(p));
        return s.is(Blocks.BIRCH_SLAB) && s.getValue(SlabBlock.TYPE)==SlabType.BOTTOM && s.getFluidState().isEmpty();
    }
    public boolean loot(VillageLayout.Loot l) {
        var p=pos(l.pos());
        return BuiltInRegistries.BLOCK.getKey(world.getBlockState(p).getBlock()).toString().equals("minecraft:"+l.kind())
                && world.getBlockEntity(p) instanceof RandomizableContainerBlockEntity container
                && container.saveWithoutMetadata().getString("LootTable").equals("dungeons_arise:chests/mushroom_village/mushroom_village_"+l.tier());
    }
    public boolean water(Cell p) {return world.getBlockState(pos(p)).is(Blocks.WATER) && world.getFluidState(pos(p)).isSource();}
    public boolean spawner(Cell p) {return world.getBlockState(pos(p)).is(Blocks.SPAWNER) && world.getBlockEntity(pos(p)) instanceof SpawnerBlockEntity;}
}
