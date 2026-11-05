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
        report.addProperty("automaticPoolEnrollment",false);var rows=new JsonArray();var accepted=new HashSet<String>();
        try(var jar=new ZipFile("libs/dimdoors-5.4.4.jar")) {
            for(var entry:Collections.list(jar.entries()).stream().filter(e->e.getName().contains("/pockets/schematic/")&&e.getName().endsWith(".schem")).sorted(Comparator.comparing(e->e.getName())).toList()) {
                String source=entry.getName();String id=source.substring(source.indexOf("/pockets/schematic/")+19,source.length()-6);
                String generator=source.replace("/pockets/schematic/","/pockets/generators/").replace(".schem",".json");
                var row=new JsonObject();row.addProperty("schematic",source);row.addProperty("target","dimdoors:"+id);
                var file=jar.getEntry(generator);
                if(file==null) {row.addProperty("status","missing_exact_generator");}
                else {
                    try(var input=jar.getInputStream(file)) {
                        var config=TagParser.parseTag(new String(input.readAllBytes(),StandardCharsets.UTF_8));
                        var ref=new CompoundTag();ref.putString("type","dimdoors:id");ref.putString("id",id);
                        try {NativeGeneratorPolicy.validate(config,ref,id);row.addProperty("status","definition_policy_pass");accepted.add(id);}
                        catch(IllegalArgumentException pending) {row.addProperty("status","pending_adapter");row.addProperty("reason",pending.getMessage());}
                    }
                }
                rows.add(row);
            }
        }
        assertEquals(125,rows.size());
        assertTrue(accepted.containsAll(Set.of("dungeon/lantredom","dungeon/lantredom_end","dungeon/lantredom_red",
                "dungeon/ruins_mansion_combination_puzzle","lab/lab_experimentation","lab/telecommunications","classic/ruins/heart_of_disorder")));
        assertFalse(accepted.contains("lab/labfinal"));assertFalse(accepted.contains("example/purpur_hallway"));
        report.add("rooms",rows);report.addProperty("schematics",rows.size());report.addProperty("definitionPolicyPasses",accepted.size());
        var path=Path.of("build/reports/native-content-policy.json");Files.createDirectories(path.getParent());
        Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report),StandardCharsets.UTF_8);
    }
}
