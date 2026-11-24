package dev.expedition.bridge.content;

import dev.expedition.bridge.domain.LabMachineProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
        if(templatePath.equals(LabMachineProfile.STRUCTURE_ID)) {
            // The whole-room ban is replaced by the verified local machine profile installed during
            // GENERATING (LabMachineInstaller). The addon is removed only from this bridge-owned copy;
            // PocketLoader definitions and ordinary living-space generators are never edited.
            // A copy that was already adapted simply validates again (idempotent preparation).
            var builder=copy.getCompound("builder");
            if(builder.contains("addons")) {
                var addons=builder.get("addons");
                if(!(addons instanceof ListTag list) || list.size()!=1
                        || !(list.get(0) instanceof CompoundTag addon)
                        || !addon.getString("type").equals("dimdoors:prevent_block_modification"))
                    throw new IllegalArgumentException("lab/labfinal addon profile changed; review the local protection profile");
                builder.remove("addons");
            }
        }
        NativeGeneratorPolicy.validate(copy,reference,templatePath);
        return copy;
    }
}
