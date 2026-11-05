package dev.expedition.bridge.content;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

/** Reviewed lazy modifiers only. Other room configurations remain pending adaptation, never stripped. */
public final class NativeGeneratorPolicy {
    private static final Set<String> MODIFIERS=Set.of("dimdoors:shell","dimdoors:rift_data","dimdoors:offset","dimdoors:relative","dimdoors:template");
    private NativeGeneratorPolicy() {}
    public static void validate(CompoundTag generator,CompoundTag reference,String templatePath) {
        if(!generator.getString("type").equals("dimdoors:schematic") || !generator.getString("id").equals(templatePath)
                || !generator.getCompound("builder").getString("type").equals("dimdoors:lazy_gen_pocket")
                || !reference.getString("type").equals("dimdoors:id") || !reference.getString("id").equals(templatePath))
            throw new IllegalArgumentException("Managed native room requires its exact lazy schematic and direct reference");
        empty(generator,"modifier_references");empty(generator,"lazy_modifiers");empty(generator,"lazy_modifier_references");
        skyAddons(generator.getCompound("builder"));empty(reference,"addons");
        empty(reference,"modifiers");empty(reference,"modifier_references");
        var modifiers=generator.get("modifiers");
        if(modifiers!=null) {
            if(!(modifiers instanceof ListTag list))throw new IllegalArgumentException("Invalid native modifiers");
            int shells=0;
            for(var tag:list) {
                if(!(tag instanceof CompoundTag modifier) || !MODIFIERS.contains(modifier.getString("type")))
                    throw new IllegalArgumentException("Native modifier needs a reviewed placement adapter: "+tag);
                if(modifier.getString("type").equals("dimdoors:relative")) {
                    if(!modifier.contains("point_a",Tag.TAG_ANY_NUMERIC)||!modifier.contains("point_b",Tag.TAG_ANY_NUMERIC)
                            ||modifier.getInt("point_a")<0||modifier.getInt("point_b")<0||modifier.getInt("point_a")==modifier.getInt("point_b")
                            ||(modifier.contains("connection")&&!Set.of("both","one_way").contains(modifier.getString("connection"))))
                        throw new IllegalArgumentException("Invalid native relative-link definition");
                }
                if(modifier.getString("type").equals("dimdoors:template")&&ResourceLocation.tryParse(modifier.getString("templateId"))==null)
                    throw new IllegalArgumentException("Invalid native continuation template");
                if(modifier.getString("type").equals("dimdoors:shell")) {
                    if(++shells>1)throw new IllegalArgumentException("Multiple native shell modifiers need adaptation");
                    if(!(modifier.get("layers") instanceof ListTag layers) || layers.isEmpty())
                        throw new IllegalArgumentException("Native shell has no reviewed layers");
                    for(var layer:layers) {
                        if(!(layer instanceof CompoundTag data))throw new IllegalArgumentException("Invalid native shell layer");
                        try {
                            int thickness=Integer.parseInt(data.getString("thickness"));
                            if(thickness<1 || thickness>256)throw new NumberFormatException();
                        } catch(NumberFormatException invalid) {
                            throw new IllegalArgumentException("Native shell thickness needs a reviewed constant extent");
                        }
                    }
                }
            }
        }
    }
    private static void skyAddons(CompoundTag builder) {
        var value=builder.get("addons");if(value==null)return;
        if(!(value instanceof ListTag list))throw new IllegalArgumentException("Invalid native addons");
        if(list.size()>1)throw new IllegalArgumentException("Additional native addons require adaptation");
        for(var tag:list) {
            if(!(tag instanceof CompoundTag addon)||!addon.getString("type").equals("dimdoors:sky")
                    ||!Set.of("minecraft:overworld","minecraft:the_nether","minecraft:the_end").contains(addon.getString("effect")))
                throw new IllegalArgumentException("Native addon needs a reviewed adapter");
            for(var key:Set.of("dayTime","moonPhase")) {
                if(addon.contains(key)&&(addon.get(key).getId()<Tag.TAG_BYTE||addon.get(key).getId()>Tag.TAG_LONG))
                    throw new IllegalArgumentException("Invalid native sky number: "+key);
            }
            if(addon.contains("moonPhase")&&(addon.getLong("moonPhase")<0||addon.getLong("moonPhase")>7))
                throw new IllegalArgumentException("Invalid native moon phase");
        }
    }
    private static void empty(CompoundTag tag,String key) {
        var value=tag.get(key);
        if(value!=null && (!(value instanceof ListTag list) || !list.isEmpty()))
            throw new IllegalArgumentException("Unreviewed native configuration: "+key);
    }
}
