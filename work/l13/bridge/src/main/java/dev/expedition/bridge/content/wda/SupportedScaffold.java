package dev.expedition.bridge.content.wda;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;

final class SupportedScaffold {
    private static BlockState state() {
        return Blocks.SCAFFOLDING.defaultBlockState().setValue(ScaffoldingBlock.DISTANCE,0)
                .setValue(ScaffoldingBlock.BOTTOM,false).setValue(ScaffoldingBlock.WATERLOGGED,false);
    }
    static boolean present(ServerLevel world,BlockPos pos) { return world.getBlockState(pos).equals(state()); }
    static void place(ServerLevel world,BlockPos pos) {
        if (ScaffoldingBlock.getDistance(world,pos)!=0) throw new IllegalStateException("Scaffold lost its support at "+pos);
        if (present(world,pos)) return;
        if (!world.getBlockState(pos).isAir() || !world.setBlock(pos,state(),2|16))
            throw new IllegalStateException("Cannot place supported scaffold at "+pos);
    }
}
