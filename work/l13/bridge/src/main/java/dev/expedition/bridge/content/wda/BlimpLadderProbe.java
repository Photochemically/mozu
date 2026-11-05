package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;

final class BlimpLadderProbe implements BlimpLadderSiteSearch.Probe {
    private final ServerLevel world;
    private final InteriorBlockProbe blocks;
    BlimpLadderProbe(ServerLevel world){this.world=world;blocks=new InteriorBlockProbe(world);}
    public InteriorBlockRules.Type at(Cell p){return blocks.at(p);}
    public boolean ladder(Cell p,int dx,int dz) {
        var state=world.getBlockState(new BlockPos(p.x(),p.y(),p.z()));
        if(!state.is(Blocks.LADDER) || !state.getFluidState().isEmpty())return false;
        var facing=state.getValue(LadderBlock.FACING);
        return facing.getStepX()==dx && facing.getStepZ()==dz;
    }
}
