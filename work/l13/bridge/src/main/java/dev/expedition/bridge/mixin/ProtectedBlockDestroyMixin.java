package dev.expedition.bridge.mixin;

import dev.expedition.bridge.portal.ManagedBlockWrites;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Refuse the normal destruction path before it drops items or fires removal effects. */
@Mixin(value=Level.class,remap=false)
public abstract class ProtectedBlockDestroyMixin {
    @Inject(method={"destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            "m_7740_(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z"},
            at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void expedition$protectDestruction(BlockPos pos,boolean drops,Entity entity,int recursion,CallbackInfoReturnable<Boolean> result) {
        if((Object)this instanceof ServerLevel level && ManagedBlockWrites.protectedPosition(level,pos))result.setReturnValue(false);
    }
}
