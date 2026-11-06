package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

record GroundHouseProbe(ServerLevel world, InteriorBlockProbe floors) implements GroundHouseRoute.Probe {
    GroundHouseProbe(ServerLevel world) { this(world,new InteriorBlockProbe(world)); }
    public Type at(Cell p) { return floors.at(p); }
    private BlockState state(Cell p) { return world.getBlockState(new BlockPos(p.x(),p.y(),p.z())); }
    public boolean spruceDoor(Cell p, Direction facing) { return floors.spruceDoor(p,facing); }
    public boolean spruceStair(Cell p, Direction facing) {
        var s=state(p);
        return s.is(Blocks.SPRUCE_STAIRS) && s.getFluidState().isEmpty() && s.getValue(StairBlock.HALF)==Half.BOTTOM
                && s.getValue(StairBlock.SHAPE)==StairsShape.STRAIGHT && s.getValue(StairBlock.FACING)==facing;
    }
    public boolean oakHalfSlab(Cell p) {
        var s=state(p);
        return s.is(Blocks.OAK_SLAB) && s.getValue(SlabBlock.TYPE)==SlabType.BOTTOM && s.getFluidState().isEmpty();
    }
    public boolean ruinTrigger(Cell ore, Cell carpet, Cell observer, Direction facing, Cell dispenser) {
        var sensor=state(observer);
        var trap=state(dispenser);
        return state(ore).is(Blocks.REDSTONE_ORE) && state(carpet).is(Blocks.BROWN_CARPET)
                && sensor.is(Blocks.OBSERVER) && sensor.getValue(ObserverBlock.FACING)==facing
                && trap.is(Blocks.DISPENSER) && trap.getValue(DispenserBlock.FACING)==Direction.UP;
    }
}
