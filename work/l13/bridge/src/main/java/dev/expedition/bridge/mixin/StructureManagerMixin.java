package dev.expedition.bridge.mixin;

import dev.expedition.bridge.worldgen.StructureLevelAccess;
import net.minecraft.world.level.*;
import org.spongepowered.asm.mixin.*;

/** Exact official/SRG aliases from pinned 1.20.1 mappings; no reflective access or off-thread SavedData reads. */
@Mixin(value=StructureManager.class,remap=false)
public abstract class StructureManagerMixin implements StructureLevelAccess {
    @Shadow(aliases={"f_220460_"},remap=false) @Final private LevelAccessor level;
    @Override public LevelAccessor expedition$level(){return level;}
}
