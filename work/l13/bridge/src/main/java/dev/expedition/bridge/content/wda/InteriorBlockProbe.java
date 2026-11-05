package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Conservative real-state check shared by selection and the final, pre-write exit validation. */
public final class InteriorBlockProbe implements InteriorSiteSearch.Probe {
    private final ServerLevel world;
    public InteriorBlockProbe(ServerLevel world){this.world=world;}
    @Override public InteriorBlockRules.Type at(BoardingRouteSearch.Cell cell) {
        var pos=new BlockPos(cell.x(),cell.y(),cell.z());var state=world.getBlockState(pos);
        String slab=state.hasProperty(BlockStateProperties.SLAB_TYPE)?state.getValue(BlockStateProperties.SLAB_TYPE).getSerializedName():"";
        String half=state.hasProperty(BlockStateProperties.HALF)?state.getValue(BlockStateProperties.HALF).getSerializedName():"";
        var kind=InteriorBlockRules.classify(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),slab,half,
                !state.getFluidState().isEmpty(),state.hasBlockEntity());
        if(kind==InteriorBlockRules.Type.SUPPORT && !state.isFaceSturdy(world,pos,Direction.UP))return InteriorBlockRules.Type.BLOCKED;
        return kind;
    }
}
