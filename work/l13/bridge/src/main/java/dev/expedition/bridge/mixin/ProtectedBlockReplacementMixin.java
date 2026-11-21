package dev.expedition.bridge.mixin;

import dev.expedition.bridge.portal.ManagedBlockWrites;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reject before section mutation, heightmap changes, block removal callbacks or block-entity removal. */
@Mixin(value=LevelChunk.class,remap=false)
public abstract class ProtectedBlockReplacementMixin {
    @Inject(method={"setBlockState","m_6978_"},at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void expedition$protectReplacement(BlockPos pos,BlockState state,boolean moving,CallbackInfoReturnable<BlockState> result) {
        var chunk=(LevelChunk)(Object)this;
        if(chunk.getLevel() instanceof ServerLevel level
                && ManagedBlockWrites.denyReplacement(level,pos,chunk.getBlockState(pos),state))result.setReturnValue(null);
    }
}
