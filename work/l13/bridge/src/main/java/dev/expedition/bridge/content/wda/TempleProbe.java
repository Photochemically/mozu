package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

record TempleProbe(ServerLevel world,InteriorBlockProbe floors) implements TempleRoute.Probe {
    TempleProbe(ServerLevel world) { this(world,new InteriorBlockProbe(world)); }
    private static BlockPos pos(Cell p) { return new BlockPos(p.x(),p.y(),p.z()); }
    public Type at(Cell p) { return floors.at(p); }
    public boolean gravel(Cell p) { return world.getBlockState(pos(p)).is(Blocks.GRAVEL) && floors.at(p.move(0,-1,0))==Type.SUPPORT; }
    public boolean stair(Cell p,Direction facing) {
        var s=world.getBlockState(pos(p));
        return s.is(Blocks.STONE_BRICK_STAIRS) && s.getValue(StairBlock.FACING)==facing
                && s.getValue(StairBlock.HALF)==Half.BOTTOM && s.getValue(StairBlock.SHAPE)==StairsShape.STRAIGHT && s.getFluidState().isEmpty();
    }
    public boolean chest(Cell p,String tier) {
        return world.getBlockState(pos(p)).is(Blocks.CHEST) && world.getBlockEntity(pos(p)) instanceof ChestBlockEntity chest
                && chest.saveWithoutMetadata().getString("LootTable").equals("dungeons_arise:chests/abandoned_temple/abandoned_temple_"+tier);
    }
    public boolean scaffold(Cell p) { return SupportedScaffold.present(world,pos(p)); }
    public void addScaffold(Cell p) { SupportedScaffold.place(world,pos(p)); }
}
