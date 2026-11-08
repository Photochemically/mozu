package dev.expedition.bridge.content.wda;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;

final class VillagePlanner {
    static final String PROCESSORS="dungeons_arise:mushroom_village_houses";
    static final String RULES="""
            [{"processor_type":"minecraft:rule","rules":[{"location_predicate":{"predicate_type":"minecraft:random_block_match","block":"minecraft:red_mushroom_block","probability":0.5},"input_predicate":{"predicate_type":"minecraft:always_true"},"output_state":{"Name":"minecraft:brown_mushroom_block","Properties":{"down":"true","east":"true","north":"true","south":"true","up":"true","west":"true"}}},{"location_predicate":{"predicate_type":"minecraft:random_block_match","block":"minecraft:brown_mushroom_block","probability":0.5},"input_predicate":{"predicate_type":"minecraft:always_true"},"output_state":{"Name":"minecraft:red_mushroom_block","Properties":{"down":"true","east":"true","north":"true","south":"true","up":"true","west":"true"}}}]},{"processor_type":"minecraft:rule","rules":[{"location_predicate":{"predicate_type":"minecraft:always_true"},"input_predicate":{"predicate_type":"minecraft:block_match","block":"minecraft:light_gray_wool"},"output_state":{"Name":"minecraft:mushroom_stem","Properties":{"down":"false","east":"false","north":"false","south":"false","up":"false","west":"false"}}}]}]
            """;
    VillagePlanner(RegistryAccess registries) {
        var list=registries.registryOrThrow(Registries.PROCESSOR_LIST).get(ResourceLocation.parse(PROCESSORS));
        var rules=JsonParser.parseString(RULES).getAsJsonArray();
        if (list==null || list.list().size()!=rules.size()) throw new IllegalArgumentException("Changed village processors");
        var ops=RegistryOps.create(JsonOps.INSTANCE,registries);var codec=StructureProcessorType.SINGLE_CODEC;
        for (int i=0;i<rules.size();i++) {
            var expected=codec.parse(ops,rules.get(i)).result().orElseThrow();
            if (!codec.encodeStart(ops,expected).result().orElseThrow().equals(codec.encodeStart(ops,list.list().get(i)).result().orElseThrow()))
                throw new IllegalArgumentException("Unsupported original village material rule");
        }
    }
    void validate(PoolElementStructurePiece piece,CompoundTag element) {
        if (piece.getElement().getClass()!=SinglePoolElement.class || !element.getString("processors").equals(PROCESSORS))
            throw new IllegalArgumentException("Unexpected village element");
    }
}
