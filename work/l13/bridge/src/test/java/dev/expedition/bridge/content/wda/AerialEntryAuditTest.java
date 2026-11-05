package dev.expedition.bridge.content.wda;

import com.google.gson.*;
import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

/** Actual original starting pieces. Rule outputs are conservatively included, not guessed away. No Forge startup. */
class AerialEntryAuditTest {
    private JsonObject json(ZipFile jar,String path) throws Exception {
        try(var in=jar.getInputStream(Objects.requireNonNull(jar.getEntry(path)))){return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
    }
    private Cell rotate(int x,int y,int z,int r){return switch(r){case 0->new Cell(x,y,z);case 1->new Cell(-z,y,x);case 2->new Cell(-x,y,-z);default->new Cell(z,y,-x);};}
    private Type output(JsonObject s) {
        var p=s.has("Properties")?s.getAsJsonObject("Properties"):new JsonObject();
        return InteriorBlockRules.classify(s.get("Name").getAsString(),p.has("type")?p.get("type").getAsString():"",p.has("half")?p.get("half").getAsString():"",p.has("waterlogged")&&p.get("waterlogged").getAsBoolean(),false);
    }
    @Test void originalStartsRecordAvailableDecksAndExplicitlyBlockedPistonGates() throws Exception {
        var rows=new JsonArray();var missing=new ArrayList<String>();var report=new JsonObject();
        try(var jar=new ZipFile("libs/DungeonsArise-1.20.x-2.1.58-release.jar")) {
            for(String id:List.of("aviary","heavenly_challenger","heavenly_conqueror","heavenly_rider","mechanical_nest")) {
                var definition=json(jar,"data/dungeons_arise/worldgen/structure/"+id+".json");assertEquals("minecraft:jigsaw",definition.get("type").getAsString());assertFalse(definition.has("project_start_to_heightmap"));
                assertDoesNotThrow(()->AerialContentPolicy.validate("dungeons_arise:"+id,definition.get("type").getAsString(),definition.has("project_start_to_heightmap")));
                var pool=json(jar,"data/dungeons_arise/worldgen/template_pool/"+definition.get("start_pool").getAsString().split(":")[1]+".json");
                for(var raw:pool.getAsJsonArray("elements")) {
                    var element=raw.getAsJsonObject().getAsJsonObject("element");assertEquals("rigid",element.get("projection").getAsString());String template=element.get("location").getAsString();
                    var rules=new ArrayList<JsonObject>();String processors=element.get("processors").getAsString();
                    if(!processors.equals("minecraft:empty"))for(var processor:json(jar,"data/dungeons_arise/worldgen/processor_list/"+processors.split(":")[1]+".json").getAsJsonArray("processors")) {
                        var p=processor.getAsJsonObject();assertEquals("minecraft:rule",p.get("processor_type").getAsString());
                        for(var rule:p.getAsJsonArray("rules")) {
                            var r=rule.getAsJsonObject();assertEquals("minecraft:always_true",r.getAsJsonObject("location_predicate").get("predicate_type").getAsString());rules.add(r);
                        }
                    }
                    CompoundTag n;try(var in=jar.getInputStream(jar.getEntry("data/dungeons_arise/structures/"+template.split(":")[1]+".nbt"))){n=NbtIo.readCompressed(in);}
                    var palette=n.getList("palette",Tag.TAG_COMPOUND);var size=n.getList("size",Tag.TAG_INT);
                    for(int rotation=0;rotation<4;rotation++) {
                        var cells=new HashMap<Cell,Type>();
                        for(var block:n.getList("blocks",Tag.TAG_COMPOUND)) {
                            var b=(CompoundTag)block;var state=palette.getCompound(b.getInt("state"));String name=state.getString("Name");var props=state.getCompound("Properties");
                            boolean joint=name.equals("minecraft:jigsaw");if(joint)name=b.getCompound("nbt").getString("final_state");if(name.equals("minecraft:structure_void"))continue;
                            var kind=InteriorBlockRules.classify(name,props.getString("type"),props.getString("half"),props.getString("waterlogged").equals("true"),b.contains("nbt")&&!joint);
                            for(var rule:rules) {
                                var input=rule.getAsJsonObject("input_predicate");String type=input.get("predicate_type").getAsString();
                                assertTrue(Set.of("minecraft:block_match","minecraft:random_block_match","minecraft:random_blockstate_match").contains(type));
                                String inputId=input.has("block")?input.get("block").getAsString():input.getAsJsonObject("block_state").get("Name").getAsString();
                                // Any possible different output is blocked, even if a probability or property might skip it.
                                if(name.equals(inputId) && output(rule.getAsJsonObject("output_state"))!=kind)kind=Type.BLOCKED;
                            }
                            var pos=b.getList("pos",Tag.TAG_INT);cells.put(rotate(pos.getInt(0),pos.getInt(1),pos.getInt(2),rotation).move(-256,16,-512),kind);
                        }
                        var corners=List.of(rotate(0,0,0,rotation),rotate(size.getInt(0)-1,0,0,rotation),rotate(0,0,size.getInt(2)-1,rotation),rotate(size.getInt(0)-1,0,size.getInt(2)-1,rotation));
                        var bounds=new BlockBounds(corners.stream().mapToInt(Cell::x).min().orElseThrow(),0,corners.stream().mapToInt(Cell::z).min().orElseThrow(),
                                corners.stream().mapToInt(Cell::x).max().orElseThrow(),size.getInt(1)-1,corners.stream().mapToInt(Cell::z).max().orElseThrow()).move(-256,16,-512);
                        InteriorSiteSearch.Probe probe=p->cells.getOrDefault(p,Type.AIR);
                        var search=new AerialSiteSearch(new InteriorRoom(bounds,List.of()),probe,List.of());int batches=0;
                        while(!search.complete() && batches++<200000)search.step(256);assertTrue(search.complete());
                        var row=new JsonObject();row.addProperty("structure",id);row.addProperty("template",template);row.addProperty("rotation",rotation);row.addProperty("processorRulesConsidered",rules.size());
                        if(search.result().isEmpty()){missing.add(template+" rotation="+rotation);row.addProperty("status","requires_connected_bridge_adapter_not_root_only_search");}
                        else {var site=search.result().orElseThrow();assertTrue(InteriorSiteSearch.clearPlatform(site,probe));row.add("arrival",new Gson().toJsonTree(site));}
                        rows.add(row);
                    }
                }
            }
            assertTrue(json(jar,"data/dungeons_arise/worldgen/structure/small_blimp.json").has("project_start_to_heightmap"));
        }
        report.add("cases",rows);report.addProperty("safeDeckCases",rows.size()-missing.size());report.addProperty("bridgeAdapterRequiredCases",missing.size());report.addProperty("runtimeAccepted",false);report.addProperty("formalPoolEnrollment",false);
        report.addProperty("scope","Original root pieces with conservative processor outcomes; not full assembly, surrounding pieces, real collision, combat or restart acceptance");
        Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/aerial-entry-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        assertEquals(48,rows.size());
        assertEquals(java.util.stream.IntStream.range(0,4).mapToObj(r->"dungeons_arise:mechanical_nest/mechanical_nest_start_0 rotation="+r).toList(),missing,
                "Only the mechanical root requires its connected bridge adapter; do not silently drop other candidates");
    }
}
