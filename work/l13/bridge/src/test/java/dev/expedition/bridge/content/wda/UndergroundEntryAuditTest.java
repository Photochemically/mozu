package dev.expedition.bridge.content.wda;

import com.google.gson.*;
import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

/** Original starting templates, buried in solid rock, four rotations. Not an assembled-dungeon/runtime acceptance. */
class UndergroundEntryAuditTest {
    private Cell rotate(int x,int y,int z,int r) {
        return switch(r){case 0->new Cell(x,y,z);case 1->new Cell(-z,y,x);case 2->new Cell(-x,y,-z);default->new Cell(z,y,-x);};
    }
    @Test void originalUndergroundStartsHaveSafePlatformsConnectedToTheirPassages() throws Exception {
        var report=new JsonObject();var rows=new JsonArray();var missing=new ArrayList<String>();
        try(var jar=new ZipFile("libs/DungeonsArise-1.20.x-2.1.58-release.jar")) {
            for(String structure:List.of("foundry","mining_system")) {
                String pool=structure.equals("foundry")?"foundry_start":"mining_system_main";
                JsonObject definition,poolJson;
                try(var input=jar.getInputStream(jar.getEntry("data/dungeons_arise/worldgen/structure/"+structure+".json"))) {
                    definition=JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                }
                assertFalse(definition.has("project_start_to_heightmap"));
                assertEquals("minecraft:jigsaw",definition.get("type").getAsString());
                assertEquals("dungeons_arise:underworld/"+structure+"/"+pool,definition.get("start_pool").getAsString());
                try(var input=jar.getInputStream(jar.getEntry("data/dungeons_arise/worldgen/template_pool/underworld/"+structure+"/"+pool+".json"))) {
                    poolJson=JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                }
                for(var element:poolJson.getAsJsonArray("elements")) {
                    var spec=element.getAsJsonObject().getAsJsonObject("element");String location=spec.get("location").getAsString();
                    assertEquals("rigid",spec.get("projection").getAsString());assertEquals("minecraft:empty",spec.get("processors").getAsString());
                    CompoundTag n;
                    try(var in=jar.getInputStream(Objects.requireNonNull(jar.getEntry("data/dungeons_arise/structures/"+location.substring(location.indexOf(':')+1)+".nbt")))) {n=NbtIo.readCompressed(in);}
                    var palette=n.getList("palette",Tag.TAG_COMPOUND);var size=n.getList("size",Tag.TAG_INT);
                    for(int rotation=0;rotation<4;rotation++) {
                        var cells=new HashMap<Cell,Type>();var passages=new ArrayList<Cell>();
                        for(var raw:n.getList("blocks",Tag.TAG_COMPOUND)) {
                            var b=(CompoundTag)raw;var pos=b.getList("pos",Tag.TAG_INT);var state=palette.getCompound(b.getInt("state"));
                            String id=state.getString("Name");var properties=state.getCompound("Properties");
                            var cell=rotate(pos.getInt(0),pos.getInt(1),pos.getInt(2),rotation).move(-160,-40,-320);
                            if(id.equals("minecraft:jigsaw")) {
                                id=b.getCompound("nbt").getString("final_state");
                                if(id.equals("minecraft:air") && properties.getString("orientation").matches("(north|south|east|west)_.*"))passages.add(cell);
                            }
                            if(id.equals("minecraft:structure_void"))continue;
                            cells.put(cell,InteriorBlockRules.classify(id,properties.getString("type"),properties.getString("waterlogged").equals("true"),
                                    b.contains("nbt") && !state.getString("Name").equals("minecraft:jigsaw")));
                        }
                        var corners=List.of(rotate(0,0,0,rotation),rotate(size.getInt(0)-1,0,0,rotation),rotate(0,0,size.getInt(2)-1,rotation),rotate(size.getInt(0)-1,0,size.getInt(2)-1,rotation));
                        var box=new BlockBounds(corners.stream().mapToInt(Cell::x).min().orElseThrow(),0,corners.stream().mapToInt(Cell::z).min().orElseThrow(),
                                corners.stream().mapToInt(Cell::x).max().orElseThrow(),size.getInt(1)-1,corners.stream().mapToInt(Cell::z).max().orElseThrow()).move(-160,-40,-320);
                        InteriorSiteSearch.Probe probe=p->cells.getOrDefault(p,Type.SUPPORT); // Missing/structure-void cells retain the rock foundation.
                        var search=new InteriorSiteSearch(new InteriorRoom(box,passages),probe,List.of());int batches=0;
                        while(!search.complete() && batches++<2000)search.step(128);assertTrue(search.complete());
                        var row=new JsonObject();row.addProperty("template",location);row.addProperty("rotation",rotation);row.addProperty("passages",passages.size());
                        if(search.result().isPresent()) {
                            var site=search.result().orElseThrow();assertTrue(InteriorSiteSearch.clearPlatform(site,probe));
                            assertEquals(Type.AIR,cells.get(site));assertEquals(Type.AIR,cells.get(site.move(0,1,0)));
                            row.add("arrival",new Gson().toJsonTree(site));
                        } else missing.add(location+" rotation="+rotation);
                        rows.add(row);
                    }
                }
            }
        }
        report.add("cases",rows);report.addProperty("runtimeAccepted",false);report.addProperty("formalPoolEnrollment",false);
        report.addProperty("scope","Five original starting pieces only; full random graph, additional exits, fluids, entities and actual collision still require runtime acceptance");
        Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/underground-entry-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        assertEquals(20,rows.size());assertTrue(missing.isEmpty(),"No safe entry in: "+missing);
    }
}
