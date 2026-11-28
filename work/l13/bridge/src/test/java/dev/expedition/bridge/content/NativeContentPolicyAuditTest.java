package dev.expedition.bridge.content;

import com.google.gson.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

/** Exact dependency definitions, not a claim that Forge loaded/placed every room successfully. */
class NativeContentPolicyAuditTest {
    @Test void auditAllOriginalSchematicsWithoutEnrollingAnyInTheGameplayPool() throws Exception {
        var report=new JsonObject();report.addProperty("runtimeAcceptancePassed",false);report.addProperty("definitionPolicyOnly",true);
        report.addProperty("automaticPoolEnrollment",false);var rows=new JsonArray();var accepted=new HashSet<String>();int adapted=0;
        try(var jar=new ZipFile("libs/dimdoors-5.4.4.jar")) {
            for(var entry:Collections.list(jar.entries()).stream().filter(e->e.getName().contains("/pockets/schematic/")&&e.getName().endsWith(".schem")).sorted(Comparator.comparing(e->e.getName())).toList()) {
                String source=entry.getName();String id=source.substring(source.indexOf("/pockets/schematic/")+19,source.length()-6);
                String generator=source.replace("/pockets/schematic/","/pockets/generators/").replace(".schem",".json");
                var row=new JsonObject();row.addProperty("schematic",source);row.addProperty("target","dimdoors:"+id);
                var file=jar.getEntry(generator);
                var bridgeResource="/data/dimdoors/pockets/generators/"+id+".json";
                var bridgeStream=file==null?getClass().getResourceAsStream(bridgeResource):null;
                if(file==null && bridgeStream==null) {row.addProperty("status","missing_exact_generator");}
                else {
                    var config=file!=null
                            ?(CompoundTag)TagParser.parseTag(new String(jar.getInputStream(file).readAllBytes(),StandardCharsets.UTF_8))
                            :(CompoundTag)TagParser.parseTag(new String(bridgeStream.readAllBytes(),StandardCharsets.UTF_8));
                    if(file!=null)row.addProperty("generatorTarget","dimdoors:"+config.getString("id"));
                    else row.addProperty("generatorTarget","dimdoors:"+id);
                    row.addProperty("originalBuilder",config.contains("builder")?config.getCompound("builder").getString("type"):"implicit_immediate");
                    var ref=new CompoundTag();ref.putString("type","dimdoors:id");ref.putString("id",id);
                    try {
                        NativeDefinitionAdapter.prepare(config,ref,id);row.addProperty("status","definition_policy_pass");accepted.add(id);
                        if(!config.contains("builder")){row.addProperty("adapter","isolated_native_lazy_builder");adapted++;}
                        else if(file==null){row.addProperty("adapter","bridge_generator_definition");}
                    }
                    catch(IllegalArgumentException pending) {row.addProperty("status","pending_adapter");row.addProperty("reason",pending.getMessage());}
                }
                rows.add(row);
            }
        }
        assertEquals(125,rows.size());
        assertTrue(accepted.containsAll(Set.of("dungeon/lantredom","dungeon/lantredom_end","dungeon/lantredom_red",
                "dungeon/ruins_mansion_combination_puzzle","lab/lab_experimentation","lab/telecommunications","classic/ruins/heart_of_disorder")));
        assertTrue(accepted.contains("lab/labfinal"));
        assertTrue(accepted.containsAll(Set.of("dungeon/jungle_riddle_fixed","lab/telecommunication")),
                "bridge-provided generator definitions for the heavy machine rooms must pass the definition policy");
        assertTrue(accepted.containsAll(Set.of("example/purpur_hallway","myth/eden","myth/sewer","myth/timoria","nether/nether_farm","nether/nether_spawners")));
        assertEquals(107,accepted.size());assertEquals(6,adapted);report.addProperty("adaptedImmediateDefinitions",adapted);
        report.add("rooms",rows);report.addProperty("schematics",rows.size());report.addProperty("definitionPolicyPasses",accepted.size());
        var path=Path.of("build/reports/native-content-policy.json");Files.createDirectories(path.getParent());
        Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report),StandardCharsets.UTF_8);
    }
}
