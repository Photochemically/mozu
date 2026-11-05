package dev.expedition.bridge.mixin;

import dev.expedition.bridge.content.NativeGenerationGuard;
import org.dimdev.dimdoors.pockets.generator.SchematicGenerator;
import org.dimdev.dimdoors.world.pocket.PocketDirectory;
import org.dimdev.dimdoors.world.pocket.type.Pocket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=SchematicGenerator.class,remap=false)
public abstract class NativeSchematicAllocationMixin {
    @Redirect(method="prepareAndPlacePocket",at=@At(value="INVOKE",target="Lorg/dimdev/dimdoors/world/pocket/PocketDirectory;newPocket(Lorg/dimdev/dimdoors/world/pocket/type/Pocket$PocketBuilder;)Lorg/dimdev/dimdoors/world/pocket/type/Pocket;"),require=1,allow=1,remap=false)
    private Pocket expedition$reserveBeforeTemplate(PocketDirectory directory,Pocket.PocketBuilder<?,?> builder) {
        return NativeGenerationGuard.allocate(directory,builder);
    }
}
