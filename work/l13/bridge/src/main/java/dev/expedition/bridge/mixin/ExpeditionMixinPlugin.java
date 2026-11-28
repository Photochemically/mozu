package dev.expedition.bridge.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

/**
 * Applies the optional Create disassembly guard only when the Create runtime is actually
 * present; instances without Create must not fail mixin application.
 */
public final class ExpeditionMixinPlugin implements IMixinConfigPlugin {
    private static final String CREATE_ENTITY = "com.simibubi.create.content.contraptions.AbstractContraptionEntity";
    private static final String CREATE_GUARD = "dev.expedition.bridge.mixin.CreateDisassemblyGuardMixin";

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (CREATE_GUARD.equals(mixinClassName)) {
            try {
                Class.forName(CREATE_ENTITY, false, ExpeditionMixinPlugin.class.getClassLoader());
                return true;
            } catch (ClassNotFoundException | LinkageError missing) {
                return false;
            }
        }
        return true;
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
