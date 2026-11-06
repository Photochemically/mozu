package dev.expedition.bridge.mixin;

import dev.expedition.bridge.content.wda.WdaBuildQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevent multi-tick construction from flooding a still unfinished hull. Never affects active/existing instances. */
@Mixin(value=FlowingFluid.class,remap=false)
public abstract class FluidGenerationMixin {
    @Inject(method={"tick","m_6292_"},at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void expedition$stageFluid(Level level,BlockPos pos,FluidState state,CallbackInfo callback) {
        if(level instanceof ServerLevel server && (state.is(FluidTags.WATER) || state.is(FluidTags.LAVA))
                && WdaBuildQueue.INSTANCE.deferFluidTick(server,pos,state))callback.cancel();
    }
}
