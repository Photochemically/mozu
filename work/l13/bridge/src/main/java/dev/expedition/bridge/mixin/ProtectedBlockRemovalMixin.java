package dev.expedition.bridge.mixin;

import dev.expedition.bridge.portal.ManagedBlockWrites;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Silent removal without drops or callbacks (enderman pickup, fire burnout, some modded mobs)
 * bypasses both destroyBlock and chunk setBlockState. Refuse it for protected positions so the
 * removal reports failure and the device stays in place.
 */
@Mixin(value=Level.class,remap=false)
public abstract class ProtectedBlockRemovalMixin {
    @Inject(method={"removeBlock","m_7471_"},at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void expedition$protectSilentRemoval(BlockPos pos,boolean moving,CallbackInfoReturnable<Boolean> result) {
        if((Object)this instanceof ServerLevel level && ManagedBlockWrites.protectedPosition(level,pos))result.setReturnValue(false);
    }
}
