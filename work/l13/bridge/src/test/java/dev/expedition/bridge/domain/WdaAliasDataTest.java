package dev.expedition.bridge.domain;

import com.google.gson.*;
import dev.expedition.bridge.compat.WdaAliasData;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class WdaAliasDataTest {
    private static final Path WDA=Path.of("libs/DungeonsArise-1.20.x-2.1.58-release.jar");
    private Map<String,byte[]> sources() throws Exception {
        var map=new HashMap<String,byte[]>();var paths=new HashSet<String>();
        WdaAliasData.ALIASES.forEach(a->paths.add(a.source()));
        WdaAliasData.SUBSTITUTIONS.forEach(s->{paths.add(s.source());paths.add(s.pool());});
        try(var jar=new ZipFile(WDA.toFile())) {
            for(var path:paths)try(var input=jar.getInputStream(Objects.requireNonNull(jar.getEntry("data/dungeons_arise/"+path),path))) {
                map.put(path,input.readAllBytes());
            }
        }
        return map;
    }
    private JsonObject parse(byte[] bytes){return JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();}
    @Test void aliasesPreserveEveryPoolChoiceAndTheOriginalTemplateBytes() throws Exception {
        var source=sources();var fixed=WdaAliasData.build(source::get);assertEquals(6,fixed.size());
        var alias=WdaAliasData.ALIASES.get(0);var original=parse(source.get(alias.source()));var patched=parse(fixed.get(alias.missing()));
        assertEquals("dungeons_arise:mechanical_nest/mechanical_nest_decoration",patched.get("name").getAsString());
        patched.add("name",original.get("name"));assertEquals(original,patched);
        for(var template:WdaAliasData.ALIASES)if(!template.pool()) {
            assertArrayEquals(source.get(template.source()),fixed.get(template.missing()));
            assertNotSame(source.get(template.source()),fixed.get(template.missing()));
        }
    }
    @Test void originalExactNamesWinAndMissingCanonicalSourcesNeverBecomeEmptyTemplates() throws Exception {
        var source=sources();WdaAliasData.ALIASES.forEach(a->source.put(a.missing(),new byte[]{1}));
        WdaAliasData.SUBSTITUTIONS.forEach(s->source.put(s.missing(),new byte[]{2}));
        assertTrue(WdaAliasData.build(source::get).isEmpty());
        for(var alias:WdaAliasData.ALIASES) {
            var missing=sources();missing.remove(alias.source());
            assertThrows(IllegalArgumentException.class,()->WdaAliasData.build(missing::get),alias.source());
        }
    }
    @Test void approvedSubstitutesResolveBothGapsWithoutRemovingTheRuntimeGate() throws Exception {
        var source=sources();var fixed=WdaAliasData.build(source::get);
        var missing=WdaKnownResourceGaps.missing("dungeons_arise:mechanical_nest",
                id->fixed.containsKey("worldgen/template_pool/"+id.split(":")[1]+".json"),
                id->fixed.containsKey("structures/"+id.split(":")[1]+".nbt"));
        assertTrue(missing.isEmpty());
        assertTrue(WdaKnownResourceGaps.missing("dungeons_arise:bandit_village",id->true,id->fixed.containsKey("structures/"+id.split(":")[1]+".nbt")).isEmpty());
        assertFalse(WdaKnownResourceGaps.missing("dungeons_arise:bandit_village",id->true,id->false).isEmpty());
    }
    @Test void foundryRetainsFullOriginalPoolIncludingEmptyOptionAndThornbornResolvesWithoutReplacingPools() throws Exception {
        var source=sources();var fixed=WdaAliasData.build(source::get);var alias=WdaAliasData.ALIASES.get(3);
        var original=parse(source.get(alias.source()));var repaired=parse(fixed.get(alias.missing()));
        assertEquals("dungeons_arise:underworld/foundry/foundry_corridor_gears",repaired.get("name").getAsString());
        assertEquals(14,repaired.getAsJsonArray("elements").size());
        var empty=repaired.getAsJsonArray("elements").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(e->e.getAsJsonObject("element").get("element_type").getAsString().equals("minecraft:empty_pool_element")).findFirst().orElseThrow();
        assertEquals(3,empty.get("weight").getAsInt());repaired.add("name",original.get("name"));assertEquals(original,repaired);
        assertTrue(WdaKnownResourceGaps.missing("dungeons_arise:foundry",id->fixed.containsKey("worldgen/template_pool/"+id.split(":")[1]+".json"),id->false).isEmpty());
        assertTrue(WdaKnownResourceGaps.missing("dungeons_arise:thornborn_towers",id->false,id->fixed.containsKey("structures/"+id.split(":")[1]+".nbt")).isEmpty());
        assertEquals(2,fixed.keySet().stream().filter(k->k.startsWith("worldgen/template_pool/")).count());
    }
    @Test void substituteBytesAreIndependentAndDoNotOverrideTheOwningPools() throws Exception {
        var source=sources();var fixed=WdaAliasData.build(source::get);
        for(var s:WdaAliasData.SUBSTITUTIONS) {
            assertArrayEquals(source.get(s.source()),fixed.get(s.missing()));
            assertNotSame(source.get(s.source()),fixed.get(s.missing()));
            assertFalse(fixed.containsKey(s.pool()));
            byte original=source.get(s.source())[0];fixed.get(s.missing())[0]^=1;
            assertEquals(original,source.get(s.source())[0]);
        }
    }
    @Test void changedSourceOrPoolIsRejectedInsteadOfApplyingAStaleSubstitution() throws Exception {
        for(var s:WdaAliasData.SUBSTITUTIONS)for(var path:List.of(s.source(),s.pool())) {
            var source=sources();source.get(path)[0]^=1;
            var error=assertThrows(IllegalArgumentException.class,()->WdaAliasData.build(source::get));
            assertTrue(error.getMessage().contains(path));
        }
    }
    @Test void missingOrEmptySourceAndPoolCannotBecomeEmptyReplacement() throws Exception {
        for(var s:WdaAliasData.SUBSTITUTIONS)for(var path:List.of(s.source(),s.pool())) {
            var source=sources();source.remove(path);
            assertThrows(IllegalArgumentException.class,()->WdaAliasData.build(source::get));
            source.put(path,new byte[0]);
            assertThrows(IllegalArgumentException.class,()->WdaAliasData.build(source::get));
        }
    }
    @Test void exactOriginalTemplateSkipsItsSubstitutionEvenIfTheBorrowedSourceChanged() throws Exception {
        for(var s:WdaAliasData.SUBSTITUTIONS) {
            var source=sources();source.put(s.missing(),new byte[]{3});source.remove(s.source());source.remove(s.pool());
            var fixed=WdaAliasData.build(source::get);assertFalse(fixed.containsKey(s.missing()));assertEquals(5,fixed.size());
        }
    }
    @Test void sourceAuditIsPinnedToTheActualReleaseAndMissingOriginals() throws Exception {
        assertEquals("f8f1254dbe7ed7c1d5403f20361a36f0d0587c82b71313e3f427c7f1ce7c8a90",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(WDA))));
        try(var jar=new ZipFile(WDA.toFile())) {
            for(var s:WdaAliasData.SUBSTITUTIONS)assertNull(jar.getEntry("data/dungeons_arise/"+s.missing()));
        }
    }
}
