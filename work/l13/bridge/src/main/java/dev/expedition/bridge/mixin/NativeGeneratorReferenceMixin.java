package dev.expedition.bridge.mixin;

import dev.expedition.bridge.content.NativeGenerationGuard;
import org.dimdev.dimdoors.pockets.PocketGenerationContext;
import org.dimdev.dimdoors.pockets.generator.*;
import org.dimdev.dimdoors.pockets.virtual.reference.PocketGeneratorReference;
import org.dimdev.dimdoors.world.pocket.type.LazyGenerationPocket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=PocketGeneratorReference.class,remap=false)
public abstract class NativeGeneratorReferenceMixin {
    @Redirect(method="prepareAndPlacePocket",at=@At(value="INVOKE",target="Lorg/dimdev/dimdoors/pockets/virtual/reference/PocketGeneratorReference;getReferencedPocketGenerator(Lorg/dimdev/dimdoors/pockets/PocketGenerationContext;)Lorg/dimdev/dimdoors/pockets/generator/PocketGenerator;"),require=1,allow=1,remap=false)
    private PocketGenerator expedition$isolate(PocketGeneratorReference reference,PocketGenerationContext context) {
        return NativeGenerationGuard.isolatedGenerator(reference,context);
    }
    @Redirect(method="prepareAndPlacePocket",at=@At(value="INVOKE",target="Lorg/dimdev/dimdoors/pockets/generator/LazyPocketGenerator;attachToPocket(Lorg/dimdev/dimdoors/world/pocket/type/LazyGenerationPocket;)V"),require=1,allow=1,remap=false)
    private void expedition$checkFinalShell(LazyPocketGenerator generator,LazyGenerationPocket pocket) {
        NativeGenerationGuard.attach(generator,pocket);
    }
}
