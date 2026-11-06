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

/** Actual original well and complete hub at all rotations, with each optional spawner variant. */
class ScorchedWellAuditTest {
    private record State(Type type,String block,String level){}
    private JsonObject json(ZipFile jar,String path) throws Exception {try(var in=jar.getInputStream(Objects.requireNonNull(jar.getEntry(path)))){return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}}
    private CompoundTag template(ZipFile jar,String id) throws Exception {try(var in=jar.getInputStream(Objects.requireNonNull(jar.getEntry("data/dungeons_arise/structures/"+id.split(":")[1]+".nbt")))){return NbtIo.readCompressed(in);}}
    private ScorchedWellLayout.Piece place(String id,CompoundTag n,Cell origin,int rotation,Map<Cell,State> cells) {
        var p=new ScorchedWellLayout.Piece(id,origin,rotation,new BlockBounds(0,0,0,0,0,0),List.of());var lava=new ArrayList<Cell>();
        var palette=n.getList("palette",Tag.TAG_COMPOUND);var size=n.getList("size",Tag.TAG_INT);
        for(var raw:n.getList("blocks",Tag.TAG_COMPOUND)) {
            var b=(CompoundTag)raw;var state=palette.getCompound(b.getInt("state"));var props=state.getCompound("Properties");var pos=b.getList("pos",Tag.TAG_INT);var q=p.at(pos.getInt(0),pos.getInt(1),pos.getInt(2));
            String name=state.getString("Name");boolean joint=name.equals("minecraft:jigsaw");if(joint)name=b.getCompound("nbt").getString("final_state");
            if(name.equals("minecraft:structure_void")||name.equals("minecraft:structure_block"))continue;
            if(name.equals("minecraft:lava"))lava.add(q);
            cells.put(q,new State(InteriorBlockRules.classify(name,props.getString("type"),props.getString("half"),props.getString("waterlogged").equals("true"),b.contains("nbt")&&!joint),name,props.getString("level")));
        }
        return new ScorchedWellLayout.Piece(id,origin,rotation,p.expectedBounds(size.getInt(0),size.getInt(1),size.getInt(2)),lava);
    }
    private ScorchedWellSiteSearch.Probe probe(Map<Cell,State> cells,IslandPlan island) {
        return new ScorchedWellSiteSearch.Probe() {
            public Type at(Cell p){var s=cells.get(p);if(s!=null)return s.type();var c=island.solidColumn(p.x(),p.z());return c!=null&&p.y()>=c.bottom()&&p.y()<=c.top()?Type.SUPPORT:Type.AIR;}
            public boolean lava(Cell p){var s=cells.get(p);return s!=null&&s.block().equals("minecraft:lava");}
        };
    }
    private ScorchedWellSiteSearch search(ScorchedWellLayout layout,ScorchedWellSiteSearch.Probe probe) {var s=new ScorchedWellSiteSearch(layout,probe);int n=0;while(!s.complete()&&n++<1000)s.step(17);assertTrue(s.complete());return s;}
    @Test void originalWellApproachKeepsLavaAndShaftAcrossRotationsAndSpawnerVariants() throws Exception {
        var rows=new JsonArray();
        try(var jar=new ZipFile("libs/DungeonsArise-1.20.x-2.1.58-release.jar")) {
            var def=json(jar,"data/dungeons_arise/worldgen/structure/scorched_mines.json");assertEquals(-14,def.getAsJsonObject("start_height").get("absolute").getAsInt());assertEquals("WORLD_SURFACE_WG",def.get("project_start_to_heightmap").getAsString());
            var templates=new HashMap<String,CompoundTag>();
            for(String pool:List.of("start","well","hub","corridors","terminators","spawners"))for(var raw:json(jar,"data/dungeons_arise/worldgen/template_pool/underworld/scorched_mines/scorched_mines_"+pool+".json").getAsJsonArray("elements")) {
                var e=raw.getAsJsonObject().getAsJsonObject("element");assertEquals("rigid",e.get("projection").getAsString());assertEquals("minecraft:empty",e.get("processors").getAsString());var id=e.get("location").getAsString();templates.put(id,template(jar,id));
            }
            assertEquals(ScorchedWellLayout.SUFFIXES,templates.keySet().stream().map(id->id.substring(ScorchedWellLayout.PREFIX.length())).collect(java.util.stream.Collectors.toSet()));
            for(var entry:templates.entrySet())for(var e:ScorchedWellJoints.forTemplate(entry.getKey())) {
                var n=entry.getValue();var palette=n.getList("palette",Tag.TAG_COMPOUND);
                var found=n.getList("blocks",Tag.TAG_COMPOUND).stream().map(t->(CompoundTag)t).filter(b->{var p=b.getList("pos",Tag.TAG_INT);return p.getInt(0)==e.x()&&p.getInt(1)==e.y()&&p.getInt(2)==e.z();}).toList();
                assertEquals(1,found.size());var b=found.get(0);var state=palette.getCompound(b.getInt("state"));var tag=b.getCompound("nbt");
                assertEquals("minecraft:jigsaw",state.getString("Name"));assertEquals(e.front()+"_"+e.top(),state.getCompound("Properties").getString("orientation"));
                assertEquals(e.name(),tag.getString("name"));assertEquals(e.target(),tag.getString("target"));assertEquals(e.pool(),tag.getString("pool"));assertEquals(e.alignment(),tag.getString("joint"));assertEquals("minecraft:air",tag.getString("final_state"));
            }
            for(int rotation=0;rotation<4;rotation++)for(int variant=-1;variant<3;variant++) {
                var cells=new HashMap<Cell,State>();var pieces=new ArrayList<ScorchedWellLayout.Piece>();String prefix=ScorchedWellLayout.PREFIX;
                var well=place(prefix+"well_0",templates.get(prefix+"well_0"),new Cell(-200,81,300),rotation,cells);pieces.add(well);
                int[][] offsets={{-19,-26,-19},{5,-26,-19},{5,-26,5},{-19,-26,5}};
                for(int i=0;i<4;i++){var o=offsets[i];pieces.add(place(prefix+"hub_part_"+i,templates.get(prefix+"hub_part_"+i),well.at(o[0],o[1],o[2]),rotation,cells));}
                if(variant>=0)for(var p:List.copyOf(pieces)) {
                    var n=templates.get(p.template());var palette=n.getList("palette",Tag.TAG_COMPOUND);
                    for(var raw:n.getList("blocks",Tag.TAG_COMPOUND)) {
                        var b=(CompoundTag)raw;if(!palette.getCompound(b.getInt("state")).getString("Name").equals("minecraft:jigsaw") || !b.getCompound("nbt").getString("target").equals("dungeons_arise:spawner"))continue;
                        var q=b.getList("pos",Tag.TAG_INT);String id=prefix+"spawner_"+variant;
                        pieces.add(place(id,templates.get(id),p.at(q.getInt(0),q.getInt(1),q.getInt(2)),rotation,cells));
                    }
                }
                var layout=ScorchedWellLayout.fromPieces(pieces);assertEquals(280,layout.lava().size());BlockBounds bounds=null;
                for(var p:pieces)bounds=bounds==null?p.bounds():IslandPlan.union(bounds,p.bounds());
                var island=new IslandPlan(bounds,layout.groundY(),8,6,24,IslandPlan.Surface.ROCK);assertEquals(94,island.firstFreeY());assertEquals(102,ReservationGeometry.plan(island.completeBounds(),16,0,256).padded().height());
                var before=new HashMap<>(cells);var probe=probe(cells,island);var selected=search(layout,probe);assertEquals(Optional.of(layout.arrival()),selected.result());assertEquals(before,cells);
                assertTrue(InteriorSiteSearch.clearPlatform(layout.arrival(),probe));assertFalse(well.bounds().contains(layout.arrival().x(),layout.arrival().y(),layout.arrival().z()));
                // The original nominal surface would plug the omitted space above the mouth; no silent tunnel.
                var nominal=new IslandPlan(bounds,96,8,6,24,IslandPlan.Surface.ROCK);assertTrue(search(layout,probe(cells,nominal)).result().isEmpty());
                var firstLava=layout.lava().get(0);cells.remove(firstLava);assertTrue(search(layout,probe).result().isEmpty());cells.put(firstLava,before.get(firstLava));
                cells.put(layout.mouth(),new State(Type.BLOCKED,"minecraft:stone",""));assertTrue(search(layout,probe).result().isEmpty());
                var row=new JsonObject();row.addProperty("rotation",rotation);row.addProperty("spawnerVariant",variant);row.addProperty("nativeLavaBlocks",280);row.addProperty("groundY",94);row.add("arrival",new Gson().toJsonTree(layout.arrival()));rows.add(row);
            }
        }
        var report=new JsonObject();report.add("cases",rows);assertEquals(16,rows.size());
        report.addProperty("jarSha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of("libs/DungeonsArise-1.20.x-2.1.58-release.jar")))));
        assertEquals("f8f1254dbe7ed7c1d5403f20361a36f0d0587c82b71313e3f427c7f1ce7c8a90",report.get("jarSha256").getAsString());
        report.addProperty("runtimeTested",false);report.addProperty("scope","Original well + complete four-part hub, with zero or each of three spawner variants x4 rotations; no branch generation, fluid settling, safe descent, collision, multiplayer or restart acceptance");
        Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/scorched-well-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
