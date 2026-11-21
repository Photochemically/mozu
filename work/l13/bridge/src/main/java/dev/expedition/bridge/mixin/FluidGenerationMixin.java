package dev.expedition.bridge.mixin;

import dev.expedition.bridge.content.wda.WdaBuildQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import dev.expedition.bridge.portal.ManagedBlockWrites;
import net.minecraft.world.level.material.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Defer unfinished-hull fluid ticks and protect recorded devices from the normal erosion path. */
@Mixin(value=FlowingFluid.class,remap=false)
public abstract class FluidGenerationMixin {
    // The normal flowing-water path drops replaced blocks before setting water. Stop before those side effects.
    @Inject(method={"spreadTo","m_6364_"},at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void expedition$keepDevices(LevelAccessor level,BlockPos pos,BlockState block,Direction direction,FluidState fluid,CallbackInfo callback) {
        if(level instanceof ServerLevel server && ManagedBlockWrites.protectedPosition(server,pos))callback.cancel();
    }
    @Inject(method={"tick","m_6292_"},at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void expedition$stageFluid(Level level,BlockPos pos,FluidState state,CallbackInfo callback) {
        if(level instanceof ServerLevel server && (state.is(FluidTags.WATER) || state.is(FluidTags.LAVA))
                && WdaBuildQueue.INSTANCE.deferFluidTick(server,pos,state))callback.cancel();
    }
}
