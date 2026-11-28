package dev.expedition.bridge.mixin;

import dev.expedition.bridge.portal.ManagedDoorProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Refuses disassembly when any block of the contraption would be written onto a protected
 * position. Create's disassemble path has no public pre-check, so this mixin is applied only
 * when the Create runtime is present (see ExpeditionMixinPlugin). Create API is reached by
 * reflection so the bridge compiles without Create's shaded libraries; the pinned signatures
 * are verified by CreateApiContractTest against the locked JAR.
 */
@Mixin(targets = "com.simibubi.create.content.contraptions.AbstractContraptionEntity", remap = false)
public abstract class CreateDisassemblyGuardMixin {
    @Inject(method = "disassemble", at = @At("HEAD"), cancellable = true, remap = false)
    private void expedition$refuseProtectedDisassembly(CallbackInfo ci) {
        var self = (Entity) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel)) return;
        try {
            Object contraption = self.getClass().getMethod("getContraption").invoke(self);
            if (contraption == null) { ci.cancel(); return; }
            Object transform = invokeInHierarchy(self, "makeStructureTransform");
            Map<?, ?> blocks = (Map<?, ?>) fieldInHierarchy(contraption, "blocks").get(contraption);
            for (Object key : blocks.keySet()) {
                var target = applyTransform(transform, key);
                if (target == null || ManagedDoorProtection.protectedAt(serverLevel, target)) {
                    ci.cancel();
                    return;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException unexpected) {
            ci.cancel();
            dev.expedition.bridge.ExpeditionBridge.LOGGER.warn("Create disassembly cancelled because its destination could not be verified", unexpected);
        }
    }

    private static BlockPos applyTransform(Object transform, Object localPos) throws ReflectiveOperationException {
        var method = transform.getClass().getMethod("apply", BlockPos.class);
        return (BlockPos) method.invoke(transform, localPos);
    }

    private static Object invokeInHierarchy(Object target, String name)
            throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                var method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(name + " on " + target.getClass());
    }

    private static Field fieldInHierarchy(Object target, String name) throws NoSuchFieldException {
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name + " on " + target.getClass());
    }
}
