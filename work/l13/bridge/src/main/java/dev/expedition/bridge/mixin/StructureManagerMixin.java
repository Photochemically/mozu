package dev.expedition.bridge.mixin;

import dev.expedition.bridge.worldgen.StructureLevelAccess;
import net.minecraft.world.level.*;
import org.spongepowered.asm.mixin.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Capture the constructor argument without aliasing a field other mods may widen with access transformers. */
@Mixin(value=StructureManager.class,remap=false)
public abstract class StructureManagerMixin implements StructureLevelAccess {
    @Unique private LevelAccessor expedition$level;
    @Inject(method="<init>(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/level/levelgen/WorldOptions;Lnet/minecraft/world/level/levelgen/structure/StructureCheck;)V",at=@At("RETURN"),require=1,remap=false)
    private void expedition$captureLevel(LevelAccessor level,WorldOptions options,StructureCheck check,CallbackInfo callback) {
        expedition$level=level;
    }
    @Override public LevelAccessor expedition$level(){return expedition$level;}
}
