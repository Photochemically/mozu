package dev.expedition.bridge.mixin;

import dev.expedition.bridge.portal.ManagedDoorProtection;
import dev.expedition.bridge.portal.ManagedEntranceTarget;
import dev.expedition.bridge.portal.ManagedExitTarget;
import dev.expedition.bridge.portal.ManagedRiftGuard;
import net.minecraft.server.level.ServerLevel;
import org.dimdev.dimdoors.block.entity.RiftBlockEntity;
import org.dimdev.dimdoors.block.entity.DetachedRiftBlockEntity;
import org.dimdev.dimdoors.block.entity.RiftData;
import org.dimdev.dimdoors.api.util.Location;
import org.dimdev.dimdoors.rift.targets.VirtualTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DD's rift configuration tool rewrites RiftBlockEntity destinations directly (setDestination),
 * bypassing every block write path. Keep bridge-owned targets and protected rift positions
 * bound to bridge-owned targets; ordinary rifts in unprotected positions stay configurable.
 */
@Mixin(value = RiftBlockEntity.class, remap = false)
public abstract class RiftDestinationGuardMixin {
    @Inject(method = "setDestination", at = @At("HEAD"), cancellable = true, remap = false)
    private void expedition$keepManagedRiftTargets(VirtualTarget target, CallbackInfo ci) {
        var self = (RiftBlockEntity) (Object) this;
        var current = self.getDestination();
        boolean currentManaged = current instanceof ManagedExitTarget || current instanceof ManagedEntranceTarget;
        boolean nextManaged = target instanceof ManagedExitTarget || target instanceof ManagedEntranceTarget;
        boolean protectedPosition = false;
        if (self.getLevel() instanceof ServerLevel level && !nextManaged) {
            protectedPosition = ManagedDoorProtection.recordedAt(level, self.getBlockPos());
        }
        if (!ManagedRiftGuard.allowsRewrite(currentManaged, protectedPosition, nextManaged)) ci.cancel();
    }

    @Inject(method = "setData", at = @At("HEAD"), cancellable = true, remap = false)
    private void expedition$guardDataReplacement(RiftData data, CallbackInfo ci) {
        if (expedition$boundRift()) ci.cancel();
    }

    @Inject(method = "copyFrom", at = @At("HEAD"), cancellable = true, remap = false)
    private void expedition$guardDataCopy(DetachedRiftBlockEntity source, CallbackInfo ci) {
        if (expedition$boundRift()) ci.cancel();
    }

    @Inject(method = "handleTargetGone", at = @At("HEAD"), cancellable = true, remap = false)
    private void expedition$guardTargetInvalidation(Location location, CallbackInfo ci) {
        if (expedition$boundRift()) ci.cancel();
    }

    @org.spongepowered.asm.mixin.Unique
    private boolean expedition$boundRift() {
        var self = (RiftBlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel level)) return false;
        var current = self.getDestination();
        return current instanceof ManagedEntranceTarget || current instanceof ManagedExitTarget
                || ManagedDoorProtection.recordedAt(level, self.getBlockPos());
    }
}
