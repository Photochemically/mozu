package dev.expedition.bridge.content;

import net.minecraft.nbt.CompoundTag;
import java.util.Set;

/** Bridge-owned copies only. Never edits PocketLoader definitions or ordinary living-space generators. */
public final class NativeDefinitionAdapter {
    private static final Set<String> IMMEDIATE_ROOMS=Set.of(
            "example/purpur_hallway","myth/eden","myth/sewer","myth/timoria","nether/nether_farm","nether/nether_spawners");
    private NativeDefinitionAdapter() {}

    public static CompoundTag prepare(CompoundTag original,CompoundTag reference,String templatePath) {
        var copy=original.copy();
        if(!copy.contains("builder")) {
            if(!IMMEDIATE_ROOMS.contains(templatePath))
                throw new IllegalArgumentException("Native immediate room needs a reviewed adapter: "+templatePath);
            // SchematicGenerator already implements native chunk placement, rift staging and loot setup.
            // Select that path without changing its template, modifiers, placement type or loot settings.
            var builder=new CompoundTag();builder.putString("type","dimdoors:lazy_gen_pocket");
            copy.put("builder",builder);
        }
        NativeGeneratorPolicy.validate(copy,reference,templatePath);
        return copy;
    }
}
