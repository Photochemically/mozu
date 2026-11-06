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

/** Complete original sparse body at four rotations/depths; no actual world, fluids or swimming physics. */
class TyphonDiveAuditTest {
    private record State(String id,Type type,CompoundTag tag) {}
    private JsonObject json(ZipFile jar,String p) throws Exception {try(var in=jar.getInputStream(Objects.requireNonNull(jar.getEntry(p)))){return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}}
    private CompoundTag template(ZipFile jar,int i) throws Exception {try(var in=jar.getInputStream(Objects.requireNonNull(jar.getEntry("data/dungeons_arise/structures/typhon/typhon_part_"+i+".nbt")))){return NbtIo.readCompressed(in);}}
    private TyphonDiveLayout.Piece place(int i,CompoundTag n,Cell origin,int rotation,Map<Cell,State> cells) {
        var p=new TyphonDiveLayout.Piece(TyphonDiveLayout.PREFIX+i,origin,rotation,new BlockBounds(0,0,0,0,0,0),List.of(),List.of());var magma=new ArrayList<Cell>();var chests=new ArrayList<Cell>();var palette=n.getList("palette",Tag.TAG_COMPOUND);
        for(var raw:n.getList("blocks",Tag.TAG_COMPOUND)) {
            var b=(CompoundTag)raw;var s=palette.getCompound(b.getInt("state"));var props=s.getCompound("Properties");var pos=b.getList("pos",Tag.TAG_INT);var q=p.at(pos.getInt(0),pos.getInt(1),pos.getInt(2));String id=s.getString("Name");
            if(id.equals("minecraft:jigsaw")){assertEquals("minecraft:structure_void",b.getCompound("nbt").getString("final_state"));continue;}
            if(id.equals("minecraft:magma_block"))magma.add(q);if(id.equals("minecraft:chest"))chests.add(q);
            assertNull(cells.put(q,new State(id,InteriorBlockRules.classify(id,props.getString("type"),props.getString("half"),props.getString("waterlogged").equals("true"),b.contains("nbt")),b.copy())),"Original pieces overlap");
        }
        return new TyphonDiveLayout.Piece(p.template(),origin,rotation,p.expectedBounds(),magma,chests);
    }
    private TyphonDiveSearch.Probe probe(Map<Cell,State> cells,IslandPlan island) {
        return new TyphonDiveSearch.Probe() {
            public Type at(Cell p){var s=cells.get(p);if(s!=null)return s.type();var c=island.solidColumn(p.x(),p.z());if(c!=null&&p.y()>=c.bottom()&&p.y()<=c.top())return Type.SUPPORT;return water(p)?Type.BLOCKED:Type.AIR;}
            public boolean water(Cell p){return !cells.containsKey(p)&&island.inBasin(p.x(),p.z())&&p.y()>=island.levels().solidFirstFreeY()&&p.y()<island.firstFreeY();}
            private boolean is(Cell p,String id){var s=cells.get(p);return s!=null&&s.id().equals("minecraft:"+id);}
            public boolean magma(Cell p){return is(p,"magma_block");}
            public boolean chest(Cell p){return is(p,"chest")&&cells.get(p).tag().getCompound("nbt").getString("LootTable").equals("dungeons_arise:chests/typhon/typhon_treasure");}
            public boolean stone(Cell p){return is(p,"stone");}
        };
    }
    private TyphonDiveSearch run(TyphonDiveLayout l,IslandPlan island,int count,TyphonDiveSearch.Probe probe) {var s=new TyphonDiveSearch(l,island,count,probe);int n=0;while(!s.complete()&&n++<10000)s.step(19);assertTrue(s.complete());return s;}
    @Test void allOriginalPiecesKeepHazardsTreasuresAndShoreDiveRoutesAcrossRotationsAndDepths() throws Exception {
        var rows=new JsonArray();
        try(var jar=new ZipFile("libs/DungeonsArise-1.20.x-2.1.58-release.jar")) {
            var def=json(jar,"data/dungeons_arise/worldgen/structure/typhon.json");assertEquals("OCEAN_FLOOR",def.get("project_start_to_heightmap").getAsString());assertEquals(-4,def.getAsJsonObject("start_height").get("absolute").getAsInt());assertEquals("dungeons_arise:aquatic/typhon/typhon_start",def.get("start_pool").getAsString());
            var ids=new HashSet<String>();for(var pool:List.of("start","main")) {
                var entries=json(jar,"data/dungeons_arise/worldgen/template_pool/aquatic/typhon/typhon_"+pool+".json").getAsJsonArray("elements");assertEquals(pool.equals("start")?1:3,entries.size());
                for(var raw:entries){var e=raw.getAsJsonObject().getAsJsonObject("element");assertEquals("rigid",e.get("projection").getAsString());assertEquals("minecraft:empty",e.get("processors").getAsString());String id=e.get("location").getAsString();ids.add(id);if(pool.equals("start"))assertEquals(TyphonDiveLayout.PREFIX+"1",id);}
            }assertEquals(Set.of(TyphonDiveLayout.PREFIX+"0",TyphonDiveLayout.PREFIX+"1",TyphonDiveLayout.PREFIX+"2"),ids);
            var templates=List.of(template(jar,0),template(jar,1),template(jar,2));int entries=0,explicitAir=0;
            for(int i=0;i<3;i++) {
                var n=templates.get(i);var size=n.getList("size",Tag.TAG_INT);for(int axis=0;axis<3;axis++)assertEquals(48,size.getInt(axis));var palette=n.getList("palette",Tag.TAG_COMPOUND);entries+=n.getList("blocks",Tag.TAG_COMPOUND).size();
                assertEquals(0,n.getList("entities",Tag.TAG_COMPOUND).size());var joints=new ArrayList<CompoundTag>();
                for(var raw:n.getList("blocks",Tag.TAG_COMPOUND)){var b=(CompoundTag)raw;String id=palette.getCompound(b.getInt("state")).getString("Name");if(id.equals("minecraft:air"))explicitAir++;if(id.equals("minecraft:jigsaw"))joints.add(b);}
                var expected=TyphonDiveLayout.joints(TyphonDiveLayout.PREFIX+i);assertEquals(expected.size(),joints.size());
                for(var e:expected) {
                    var b=joints.stream().filter(j->j.getList("pos",Tag.TAG_INT).getInt(0)==e.x()).findFirst().orElseThrow();var pos=b.getList("pos",Tag.TAG_INT);assertEquals(0,pos.getInt(1));assertEquals(0,pos.getInt(2));
                    assertEquals(e.front()+"_up",palette.getCompound(b.getInt("state")).getCompound("Properties").getString("orientation"));var tag=b.getCompound("nbt");
                    assertEquals("dungeons_arise:typhon_side_"+e.name(),tag.getString("name"));assertEquals("dungeons_arise:typhon_side_"+e.target(),tag.getString("target"));assertEquals("rollable",tag.getString("joint"));assertEquals("dungeons_arise:aquatic/typhon/typhon_main",tag.getString("pool"));assertEquals("minecraft:structure_void",tag.getString("final_state"));
                }
            }assertEquals(3898,entries);assertEquals(5,explicitAir);
            for(int rotation=0;rotation<4;rotation++)for(int depth:new int[]{4,12,32,64}) {
                var cells=new HashMap<Cell,State>();var pieces=new ArrayList<TyphonDiveLayout.Piece>();int bed=96-depth;
                var root=place(1,templates.get(1),new Cell(-200,bed-5,300),rotation,cells);pieces.add(root);pieces.add(place(0,templates.get(0),root.at(-48,0,0),rotation,cells));pieces.add(place(2,templates.get(2),root.at(48,0,0),rotation,cells));
                var bounds=pieces.stream().map(TyphonDiveLayout.Piece::bounds).reduce(IslandPlan::union).orElseThrow();assertEquals(144*48*48,bounds.width()*bounds.height()*bounds.depth());
                var island=new IslandPlan(bounds,96,8,6,24,IslandPlan.Surface.WATER,depth);var layout=TyphonDiveLayout.fromPieces(pieces,island);assertEquals(124,layout.magma().size());assertEquals(2,layout.chests().size());
                var before=new HashMap<>(cells);var probe=probe(cells,island);
                assertEquals(root.origin().y()+33,cells.entrySet().stream().filter(e->!e.getValue().id().equals("minecraft:air")).mapToInt(e->e.getKey().y()).max().orElseThrow());
                for(int count=1;count<=4;count++){var s=run(layout,island,count,probe);assertEquals(layout.shores(island,count),s.result());for(var shore:s.result()){assertTrue(InteriorSiteSearch.clearPlatform(shore,probe));assertFalse(island.inBasin(shore.x(),shore.z()));}}
                assertEquals(before,cells);assertTrue(probe.water(root.at(24,7,3)));assertEquals(Type.BLOCKED,probe.at(root.at(24,7,3)));assertEquals(Type.AIR,probe.at(root.at(46,1,0)));assertTrue(probe.chest(root.at(-9,1,24))); // original buried treasure retained
                int required=ReservationGeometry.plan(island.completeBounds(),16,0,256).padded().height();
                var missing=layout.magma().get(0);cells.remove(missing);assertTrue(run(layout,island,1,probe).result().isEmpty());cells.put(missing,before.get(missing));
                cells.put(root.at(24,7,3),new State("minecraft:stone",Type.SUPPORT,new CompoundTag()));assertTrue(run(layout,island,1,probe).result().isEmpty());
                var row=new JsonObject();row.addProperty("rotation",rotation);row.addProperty("waterDepth",depth);row.addProperty("seabedFirstFreeY",bed);row.addProperty("rootY",root.origin().y());row.addProperty("highestOriginalBlockY",root.origin().y()+33);row.addProperty("waterSurfaceFirstFreeY",96);row.addProperty("requiredHeightWithMargin",required);row.addProperty("magmaBlocks",124);row.addProperty("chests",2);row.addProperty("shoreCountsChecked","1,2,3,4");row.add("arrival",new Gson().toJsonTree(layout.shores(island,1).get(0)));rows.add(row);
            }
        }
        var report=new JsonObject();report.add("cases",rows);assertEquals(16,rows.size());report.addProperty("originalBlockEntries",3898);report.addProperty("omittedTemplateCells",3*48*48*48-3898);report.addProperty("explicitAirBlocks",5);
        report.addProperty("jarSha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of("libs/DungeonsArise-1.20.x-2.1.58-release.jar")))));assertEquals("f8f1254dbe7ed7c1d5403f20361a36f0d0587c82b71313e3f427c7f1ce7c8a90",report.get("jarSha256").getAsString());
        report.addProperty("runtimeTested",false);report.addProperty("formalDepthAssigned",false);report.addProperty("scope","Complete three-piece original NBT composition and 1..4 dry shores at 4 rotations x4 illustrative depths; no native generation execution, waterlogging, bubble physics, drowning, treasure reachability, swimming or multiplayer/restart acceptance");
        Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/typhon-dive-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
