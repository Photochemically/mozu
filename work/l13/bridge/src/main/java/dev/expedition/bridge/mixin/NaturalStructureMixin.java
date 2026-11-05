package dev.expedition.bridge.mixin;

import dev.expedition.bridge.domain.NaturalGenerationRules;
import dev.expedition.bridge.worldgen.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=ChunkGenerator.class,remap=false)
public abstract class NaturalStructureMixin {
    // Official development name and exact SRG production name; require one matching target, not two.
    @Inject(method={"tryGenerateStructure","m_223104_"},at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void expedition$replaceNaturalStarts(StructureSet.StructureSelectionEntry entry,StructureManager manager,
            RegistryAccess registries,RandomState random,StructureTemplateManager templates,long seed,ChunkAccess chunk,
            ChunkPos pos,SectionPos section,CallbackInfoReturnable<Boolean> result) {
        if(!NaturalEntryConfig.enabled())return;
        var access=((StructureLevelAccess)manager).expedition$level();
        ServerLevel level=access instanceof ServerLevel server?server:access instanceof WorldGenRegion region?region.getLevel():null;
        var key=entry.structure().unwrapKey();
        if(level!=null && key.isPresent()) {
            var id=key.get().location();
            if(NaturalGenerationRules.suppress(true,level.dimension().location().toString(),id.getNamespace(),id.getPath()))result.setReturnValue(false);
        }
    }
}
