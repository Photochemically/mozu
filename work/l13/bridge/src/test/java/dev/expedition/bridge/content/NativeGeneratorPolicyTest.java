package dev.expedition.bridge.content;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.util.zip.ZipFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativeGeneratorPolicyTest {
    @Test void installedNativeAcceptanceRoomsPreserveTheirOriginalGeneratorConfiguration() throws Exception {
        try(var jar=new ZipFile("libs/dimdoors-5.4.4.jar")) {
            for(var room:List.of("cage_monolithless","chain_t","upwards_t_treasure")) {
                String path="dungeon/"+room;
                var entry=jar.getEntry("resourcepacks/default/data/dimdoors/pockets/generators/"+path+".json");
                assertNotNull(entry);
                try(var stream=jar.getInputStream(entry)) {
                    var config=TagParser.parseTag(new String(stream.readAllBytes(),StandardCharsets.UTF_8));
                    var ref=reference();ref.putString("id",path);
                    assertDoesNotThrow(()->NativeGeneratorPolicy.validate(config,ref,path),path);
                }
            }
        }
    }
    private CompoundTag generator() throws Exception {
        return TagParser.parseTag("{type:'dimdoors:schematic',id:'dungeon/test',builder:{type:'dimdoors:lazy_gen_pocket'},modifiers:[{type:'dimdoors:shell',layers:[{block_state:'minecraft:barrier',thickness:'1'}]},{type:'dimdoors:rift_data',ids:[0]}]}");
    }
    private CompoundTag reference() throws Exception {return TagParser.parseTag("{type:'dimdoors:id',id:'dungeon/test'}");}
    @Test void relativeMechanismsContinuationTargetsAndOriginalSkyAreAcceptedWithoutRewritingConfig() throws Exception {
        var config=TagParser.parseTag("{type:'dimdoors:schematic',id:'dungeon/test',builder:{type:'dimdoors:lazy_gen_pocket',addons:[{type:'dimdoors:sky',effect:'minecraft:the_end',dayTime:6000L,moonPhase:3b}]},modifiers:[{type:'dimdoors:relative',point_a:1,point_b:2,connection:'one_way'},{type:'dimdoors:template',ids:[B;3b],templateId:'dimdoors:lab/next'}]}");
        var original=config.copy();NativeGeneratorPolicy.validate(config,reference(),"dungeon/test");assertEquals(original,config);
    }
    @Test void unsupportedProtectionAddonAndInvalidMechanismRemainBlocked() throws Exception {
        var config=generator();var ref=reference();
        config.getCompound("builder").put("addons",TagParser.parseTag("{v:[{type:'dimdoors:prevent_block_modification'}]}").get("v"));
        assertThrows(IllegalArgumentException.class,()->NativeGeneratorPolicy.validate(config,ref,"dungeon/test"));
        config.getCompound("builder").remove("addons");
        config.put("modifiers",TagParser.parseTag("{v:[{type:'dimdoors:relative',point_a:1,point_b:1}]}").get("v"));
        assertThrows(IllegalArgumentException.class,()->NativeGeneratorPolicy.validate(config,ref,"dungeon/test"));
    }
    @Test void keepsReviewedNativeContentButRejectsUnreviewedWritePaths() throws Exception {
        var tag=generator();var original=tag.copy();var ref=reference();
        assertDoesNotThrow(()->NativeGeneratorPolicy.validate(tag,ref,"dungeon/test"));assertEquals(original,tag);
        tag.getList("modifiers",10).getCompound(0).putString("type","dimdoors:door");
        assertThrows(IllegalArgumentException.class,()->NativeGeneratorPolicy.validate(tag,ref,"dungeon/test"));
        var nonLazy=generator();nonLazy.getCompound("builder").putString("type","dimdoors:pocket");
        assertThrows(IllegalArgumentException.class,()->NativeGeneratorPolicy.validate(nonLazy,ref,"dungeon/test"));
    }
    @Test void dynamicShellAndChangedReferenceRequireAdaptationRatherThanSilentRemoval() throws Exception {
        var tag=generator();var ref=reference();
        tag.getList("modifiers",10).getCompound(0).getList("layers",10).getCompound(0).putString("thickness","depth+1");
        assertThrows(IllegalArgumentException.class,()->NativeGeneratorPolicy.validate(tag,ref,"dungeon/test"));
        ref.putString("id","dungeon/other");var pristine=generator();
        assertThrows(IllegalArgumentException.class,()->NativeGeneratorPolicy.validate(pristine,ref,"dungeon/test"));
    }
}
