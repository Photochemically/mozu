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

/** Original root + compatible direct bridge at all three gate ports and all four root rotations. No game startup. */
class MechanicalBridgeAuditTest {
    private record Template(String id,CompoundTag nbt){}
    private record Placed(MechanicalBridgeLayout.Piece piece,Map<Cell,Type> cells){}
    private JsonObject json(ZipFile jar,String path) throws Exception {
        try(var in=jar.getInputStream(Objects.requireNonNull(jar.getEntry(path)))){return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
    }
    private Template template(ZipFile jar,String id) throws Exception {
        try(var in=jar.getInputStream(Objects.requireNonNull(jar.getEntry("data/dungeons_arise/structures/"+id.split(":")[1]+".nbt")))){return new Template(id,NbtIo.readCompressed(in));}
    }
    private Cell rotate(Cell p,int r){return switch(r){case 0->p;case 1->new Cell(-p.z(),p.y(),p.x());case 2->new Cell(-p.x(),p.y(),-p.z());default->new Cell(p.z(),p.y(),-p.x());};}
    private Type output(JsonObject s) {
        var p=s.has("Properties")?s.getAsJsonObject("Properties"):new JsonObject();
        return InteriorBlockRules.classify(s.get("Name").getAsString(),p.has("type")?p.get("type").getAsString():"",p.has("half")?p.get("half").getAsString():"",p.has("waterlogged")&&p.get("waterlogged").getAsBoolean(),false);
    }
    private Placed place(Template template,int rotation,Cell offset,List<JsonObject> rules) {
        var n=template.nbt();var palette=n.getList("palette",Tag.TAG_COMPOUND);var size=n.getList("size",Tag.TAG_INT);
        var cells=new HashMap<Cell,Type>();var joints=new ArrayList<MechanicalBridgeLayout.Joint>();
        for(var raw:n.getList("blocks",Tag.TAG_COMPOUND)) {
            var block=(CompoundTag)raw;var state=palette.getCompound(block.getInt("state"));var props=state.getCompound("Properties");
            var rawPos=block.getList("pos",Tag.TAG_INT);var pos=rotate(new Cell(rawPos.getInt(0),rawPos.getInt(1),rawPos.getInt(2)),rotation).move(offset.x(),offset.y(),offset.z());
            String name=state.getString("Name");boolean joint=name.equals("minecraft:jigsaw");
            if(joint) {
                var tag=block.getCompound("nbt");name=tag.getString("final_state");String orientation=props.getString("orientation");
                Cell front=switch(orientation){case "west_up"->new Cell(-1,0,0);case "east_up"->new Cell(1,0,0);case "north_up"->new Cell(0,0,-1);case "south_up"->new Cell(0,0,1);default->null;};
                if(front!=null) {front=rotate(front,rotation);joints.add(new MechanicalBridgeLayout.Joint(pos,front.x(),front.z(),tag.getString("name"),tag.getString("target"),tag.getString("pool"),name));}
            }
            if(name.equals("minecraft:structure_void"))continue;
            var kind=InteriorBlockRules.classify(name,props.getString("type"),props.getString("half"),props.getString("waterlogged").equals("true"),block.contains("nbt")&&!joint);
            for(var rule:rules) {
                var input=rule.getAsJsonObject("input_predicate");String type=input.get("predicate_type").getAsString();
                assertTrue(Set.of("minecraft:block_match","minecraft:random_block_match","minecraft:random_blockstate_match").contains(type));
                String inputId=input.has("block")?input.get("block").getAsString():input.getAsJsonObject("block_state").get("Name").getAsString();
                if(name.equals(inputId) && output(rule.getAsJsonObject("output_state"))!=kind)kind=Type.BLOCKED;
            }
            cells.put(pos,kind);
        }
        var corners=List.of(rotate(new Cell(0,0,0),rotation),rotate(new Cell(size.getInt(0)-1,0,0),rotation),
                rotate(new Cell(0,0,size.getInt(2)-1),rotation),rotate(new Cell(size.getInt(0)-1,0,size.getInt(2)-1),rotation));
        var bounds=new BlockBounds(corners.stream().mapToInt(Cell::x).min().orElseThrow(),0,corners.stream().mapToInt(Cell::z).min().orElseThrow(),
                corners.stream().mapToInt(Cell::x).max().orElseThrow(),size.getInt(1)-1,corners.stream().mapToInt(Cell::z).max().orElseThrow()).move(offset.x(),offset.y(),offset.z());
        return new Placed(new MechanicalBridgeLayout.Piece(template.id(),bounds,joints),cells);
    }
    @Test void allCompatibleNativeBridgeAttachmentsRecordSafeSitesOrExplicitRejection() throws Exception {
        var rows=new JsonArray();var successes=new TreeMap<String,Integer>();var failures=new TreeMap<String,Integer>();
        try(var jar=new ZipFile("libs/DungeonsArise-1.20.x-2.1.58-release.jar")) {
            var rules=new ArrayList<JsonObject>();
            for(var raw:json(jar,"data/dungeons_arise/worldgen/processor_list/mechanical_nest_main.json").getAsJsonArray("processors")) {
                var p=raw.getAsJsonObject();assertEquals("minecraft:rule",p.get("processor_type").getAsString());
                for(var rule:p.getAsJsonArray("rules")) {var r=rule.getAsJsonObject();assertEquals("minecraft:always_true",r.getAsJsonObject("location_predicate").get("predicate_type").getAsString());rules.add(r);}
            }
            var bridges=new ArrayList<Template>();
            for(var raw:json(jar,"data/dungeons_arise/worldgen/template_pool/mechanical_nest/mechanical_nest_bridges.json").getAsJsonArray("elements")) {
                var e=raw.getAsJsonObject().getAsJsonObject("element");assertEquals("rigid",e.get("projection").getAsString());assertEquals("dungeons_arise:mechanical_nest_main",e.get("processors").getAsString());
                var t=template(jar,e.get("location").getAsString());
                if(place(t,0,new Cell(0,0,0),rules).piece().joints().stream().anyMatch(j->j.name().equals(MechanicalBridgeLayout.BRIDGE_NAME)))bridges.add(t);
            }
            assertEquals(8,bridges.size());
            for(int rotation=0;rotation<4;rotation++) {
                var root=place(template(jar,MechanicalBridgeLayout.ROOT),rotation,new Cell(-256,16,-512),rules);
                assertThrows(IllegalArgumentException.class,()->MechanicalBridgeLayout.fromPieces(List.of(root.piece())));
                var gates=root.piece().joints().stream().filter(j->j.target().equals(MechanicalBridgeLayout.BRIDGE_NAME)).toList();assertEquals(3,gates.size());
                for(var gate:gates)for(var t:bridges) {
                    Placed bridge=null;
                    for(int r=0;r<4;r++) {
                        var candidate=place(t,r,new Cell(0,0,0),rules);
                        for(var joint:candidate.piece().joints())if(joint.name().equals(gate.target()) && joint.dx()==-gate.dx() && joint.dz()==-gate.dz()) {
                            var target=gate.pos().move(gate.dx(),0,gate.dz());var pos=joint.pos();
                            bridge=place(t,r,new Cell(target.x()-pos.x(),target.y()-pos.y(),target.z()-pos.z()),rules);
                        }
                    }
                    assertNotNull(bridge);var pieces=List.of(root.piece(),bridge.piece());var layout=MechanicalBridgeLayout.fromPieces(pieces);assertEquals(1,layout.approaches().size());
                    var cells=new HashMap<>(root.cells());cells.putAll(bridge.cells());var before=new HashMap<>(cells);
                    InteriorSiteSearch.Probe probe=p->cells.getOrDefault(p,Type.AIR);
                    var search=new MechanicalBridgeSiteSearch(layout,probe);int loops=0;
                    while(!search.complete() && loops++<200000)search.step(64);assertTrue(search.complete());assertEquals(before,cells);
                    var row=new JsonObject();row.addProperty("bridge",t.id());row.addProperty("rootRotation",rotation);row.add("gate",new Gson().toJsonTree(gate.pos()));
                    if(search.result().isPresent()) {
                        var site=search.result().orElseThrow();assertEquals(gate.pos().y(),site.y());assertTrue(InteriorSiteSearch.clearPlatform(site,probe));
                        assertTrue(bridge.piece().bounds().contains(new BlockBounds(site.x()-1,site.y()-1,site.z()-1,site.x()+1,site.y()+2,site.z()+1)));
                        assertFalse(root.piece().bounds().contains(site.x(),site.y(),site.z()));successes.merge(t.id(),1,Integer::sum);row.add("arrival",new Gson().toJsonTree(site));
                    } else {failures.merge(t.id(),1,Integer::sum);row.addProperty("status","no_safe_platform_on_this_original_bridge");}
                    rows.add(row);
                    var displaced=bridge.piece();var wrong=new MechanicalBridgeLayout.Piece(displaced.template(),displaced.bounds().move(1,0,1),displaced.joints().stream().map(j->j.move(1,0,1)).toList());
                    assertThrows(IllegalArgumentException.class,()->MechanicalBridgeLayout.fromPieces(List.of(root.piece(),wrong)));
                    assertThrows(IllegalArgumentException.class,()->MechanicalBridgeLayout.fromPieces(List.of(root.piece(),displaced,displaced)));
                }
            }
        }
        var report=new JsonObject();report.addProperty("jarSha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of("libs/DungeonsArise-1.20.x-2.1.58-release.jar")))));
        report.add("cases",rows);report.add("safeByTemplate",new Gson().toJsonTree(successes));report.add("rejectedByTemplate",new Gson().toJsonTree(failures));
        report.addProperty("runtimeTested",false);report.addProperty("scope","Root plus direct bridge only; conservative processors; no random decorations, full assembly, player collision, multiplayer or restart acceptance");
        Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/mechanical-bridge-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        assertEquals("f8f1254dbe7ed7c1d5403f20361a36f0d0587c82b71313e3f427c7f1ce7c8a90",report.get("jarSha256").getAsString());
        assertEquals(96,rows.size());
        var expected=new TreeMap<String,Integer>();
        for(String suffix:List.of("0","1","3","5","6","7","8"))expected.put(MechanicalBridgeLayout.PREFIX+"mechanical_nest_bridge_"+suffix,12);
        assertEquals(expected,successes,"Every audited full bridge must retain all three ports and all four rotations");
        assertEquals(Map.of(MechanicalBridgeLayout.PREFIX+"mechanical_nest_bridge_terminator_2",12),failures,
                "The short native terminator must not be mistaken for a safe bridge platform");
    }
}
