package dev.expedition.bridge.mixin;

import dev.expedition.bridge.portal.LivingReturnFallback;
import net.minecraft.core.Rotations;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.dimdev.dimdoors.api.util.Location;
import org.dimdev.dimdoors.rift.targets.PrivatePocketExitTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mod-owned method names are stable in the pinned DD 5.4.4 jar, so no Minecraft remapping is involved. */
@Mixin(value=PrivatePocketExitTarget.class,remap=false)
public abstract class PrivatePocketExitMixin {
    @Inject(method="receiveEntity",at=@At(value="INVOKE",target="Lorg/dimdev/dimdoors/rift/targets/LimboTarget;receiveEntity(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/Rotations;Lnet/minecraft/world/phys/Vec3;Lorg/dimdev/dimdoors/api/util/Location;)Z",remap=false),cancellable=true,require=1,remap=false)
    private void expedition$keepLivingSpaceSafe(Entity entity,Vec3 pos,Rotations angle,Vec3 velocity,Location source,CallbackInfoReturnable<Boolean> result) {
        result.setReturnValue(LivingReturnFallback.returnWithoutLimbo(entity));
    }
}
