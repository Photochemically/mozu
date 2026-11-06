package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

record BathhouseProbe(ServerLevel world, InteriorBlockProbe floors) implements BathhouseRoute.Probe {
    BathhouseProbe(ServerLevel world) { this(world,new InteriorBlockProbe(world)); }
    private static BlockPos pos(Cell p) { return new BlockPos(p.x(),p.y(),p.z()); }
    public Type at(Cell p) { return floors.at(p); }
    public boolean door(Cell p,String wood,Direction facing) {
        var block = switch (wood) {
            case "dark_oak" -> Blocks.DARK_OAK_DOOR;
            case "spruce" -> Blocks.SPRUCE_DOOR;
            case "birch" -> Blocks.BIRCH_DOOR;
            default -> throw new IllegalArgumentException("Unknown bathhouse door " + wood);
        };
        return floors.door(p,block,facing);
    }
    public boolean source(BlockPos p) {
        var state = world.getBlockState(p);
        return state.is(Blocks.WATER) && state.getFluidState().isSource();
    }
    public boolean closedTopHatch(Cell p) {
        var state = world.getBlockState(pos(p));
        return state.is(Blocks.DARK_OAK_TRAPDOOR) && state.getValue(TrapDoorBlock.HALF) == Half.TOP
                && !state.getValue(TrapDoorBlock.OPEN) && state.getFluidState().isEmpty();
    }
    private static BlockState supportedScaffold() {
        return Blocks.SCAFFOLDING.defaultBlockState().setValue(ScaffoldingBlock.DISTANCE,0)
                .setValue(ScaffoldingBlock.BOTTOM,false).setValue(ScaffoldingBlock.WATERLOGGED,false);
    }
    public boolean scaffold(Cell p) { return world.getBlockState(pos(p)).equals(supportedScaffold()); }
    public void addScaffold(Cell p) {
        var target = pos(p);
        if (ScaffoldingBlock.getDistance(world,target) != 0) throw new IllegalStateException("Scaffold lost its support at " + p);
        if (scaffold(p)) return;
        if (!world.getBlockState(target).isAir() || !world.setBlock(target,supportedScaffold(),2|16)) {
            throw new IllegalStateException("Cannot place bathhouse scaffold at " + p);
        }
    }
}
