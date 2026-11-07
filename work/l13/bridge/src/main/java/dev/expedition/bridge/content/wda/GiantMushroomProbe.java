package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

record GiantMushroomProbe(ServerLevel world, InteriorBlockProbe floors) implements GiantMushroomRoute.Probe {
    GiantMushroomProbe(ServerLevel world) { this(world,new InteriorBlockProbe(world)); }
    public Type at(Cell p) { return floors.at(p); }
    public boolean stem(Cell p) { return world.getBlockState(new BlockPos(p.x(),p.y(),p.z())).is(Blocks.MUSHROOM_STEM); }
    public boolean lootChest(BlockPos pos,String table) {
        return world.getBlockState(pos).is(Blocks.CHEST) && world.getBlockEntity(pos) instanceof ChestBlockEntity chest
                && table.equals(chest.saveWithoutMetadata().getString("LootTable")) && world.isEmptyBlock(pos.above());
    }
    public boolean skeletonSpawner(BlockPos pos) {
        if (!(world.getBlockEntity(pos) instanceof SpawnerBlockEntity cage)) return false;
        return cage.saveWithoutMetadata().getCompound("SpawnData").getCompound("entity").getString("id").equals("minecraft:skeleton");
    }
}
