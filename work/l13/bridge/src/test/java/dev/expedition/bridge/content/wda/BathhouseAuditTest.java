package dev.expedition.bridge.content.wda;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import static dev.expedition.bridge.content.wda.WdaTemplateFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class BathhouseAuditTest {
    private static final WdaStructurePlanner.Environment ENV = new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.GRASS);
    private static final BlockPos ORIGIN = new BlockPos(-89,Y-1,207);

    private record Assembly(List<Placed> pieces, Set<BlockPos> sockets, List<BlockPos> sources) {
        List<WdaTemplatePiece> graph() { return pieces.stream().map(Placed::piece).toList(); }
        BathhouseLayout layout() { return BathhouseLayout.inspect(graph(),ENV,sockets,sources); }
    }

    private static List<Placed> stack(Map<String,Template> templates,int base,int middle,int top,Rotation rotation) {
        var root = new Placed(templates.get("base_"+base),ORIGIN,rotation);
        var body = connect(root,root.template().joint("bathhouse_middle_0",Direction.UP),templates.get("middle_"+middle),Direction.DOWN);
        var roof = connect(body,body.template().joint("bathhouse_top_0",Direction.UP),templates.get("top_"+top),Direction.DOWN);
        return List.of(root,body,roof);
    }

    private static Assembly assemble(Map<String,Template> templates,int base,int middle,int top,Rotation rotation,boolean populate) {
        var core = stack(templates,base,middle,top,rotation);
        var pieces = new ArrayList<>(core);
        var sockets = new HashSet<BlockPos>();
        var sources = new ArrayList<BlockPos>();
        int variant = base+middle+top+rotation.ordinal();
        for (var piece : core) for (var block : piece.template().blocks()) {
            if (block.id().equals("minecraft:water") && block.props().getString("level").equals("0")) sources.add(piece.at(block.pos()));
            if (!block.data().getString("target").equals("dungeons_arise:bathhouse_spawner_0")) continue;
            sockets.add(piece.at(block.pos()));
            if (populate && !(piece.template().id().equals("top_3") && block.pos().equals(new BlockPos(20,6,15)))) {
                var spawner = connect(piece,block,templates.get("spawner_"+(variant++%3)),Direction.DOWN);
                pieces.add(spawner);
            }
        }
        return new Assembly(pieces,sockets,sources);
    }

    private static class Model extends Terrain implements BathhouseRoute.Probe {
        final BathhouseLayout layout;
        final Set<Cell> added = new LinkedHashSet<>();
        final IslandPlan reserved;
        Model(Assembly sample) {
            super(sample.pieces);
            layout=sample.layout();
            reserved=new IslandPlan(island.building().expand(12),Y,8,6,16,IslandPlan.Surface.GRASS);
        }
        private boolean has(Cell p,String id) {
            return !blocked.contains(p) && placed.containsKey(p) && placed.get(p).id().equals("minecraft:"+id);
        }
        public boolean door(Cell p,String wood,Direction facing) {
            var top=p.move(0,1,0);
            if (!has(p,wood+"_door") || !has(top,wood+"_door")) return false;
            var lower=placed.get(p); var upper=placed.get(top);
            var props=lower.props().copy(); props.putString("half","upper");
            return lower.props().getString("half").equals("lower") && lower.facing()==facing && upper.facing()==facing && upper.props().equals(props);
        }
        public boolean closedTopHatch(Cell p) {
            if (!has(p,"dark_oak_trapdoor")) return false;
            var props=placed.get(p).props();
            return props.getString("half").equals("top") && props.getString("open").equals("false") && props.getString("waterlogged").equals("false");
        }
        public boolean source(BlockPos pos) {
            var p=cell(pos);
            return has(p,"water") && placed.get(p).props().getString("level").equals("0");
        }
        public boolean scaffold(Cell p) { return !blocked.contains(p) && added.contains(p); }
        @Override public Type at(Cell p) { return added.contains(p) ? Type.BLOCKED : super.at(p); }
        public void addScaffold(Cell p) {
            if (scaffold(p)) return;
            assertEquals(Type.AIR,at(p));
            assertFalse(island.building().contains(p.x(),p.y(),p.z()));
            var below=p.move(0,-1,0);
            assertTrue(at(below)==Type.SUPPORT || scaffold(below));
            assertTrue(added.add(p));
        }
        void survey(int exits) {
            var route=new BathhouseRoute(layout,reserved,exits,this);
            for (int i=0;i<16000;i++) if (route.step()) return;
            fail("Route over budget");
        }
    }
    private static Cell cell(BlockPos p) { return new Cell(p.getX(),p.getY(),p.getZ()); }

    @Test void nativeTerrainMarginIncludesTheScaffoldButLeavesTheOriginalWallIntact() throws Exception {
        var model=new Model(assemble(templates("bathhouse"),4,3,1,Rotation.CLOCKWISE_90,false));
        var landing=model.layout.at(31,9,15);
        assertTrue(model.reserved.building().contains(landing.x(),landing.y(),landing.z()));
        assertFalse(model.island.building().contains(landing.x(),landing.y(),landing.z()));
        model.survey(4);
        assertTrue(model.scaffold(landing.move(0,-1,0)));
    }

    @Test void allNativeStacksFitInFourRotations() throws Exception {
        var templates=templates("bathhouse");
        int count=0;
        for (var r:Rotation.values()) for (int b=0;b<6;b++) for (int m=0;m<4;m++) for (int t=0;t<7;t++) {
            var core=stack(templates,b,m,t,r);
            var layout=BathhouseLayout.inspect(core.stream().map(Placed::piece).toList(),ENV,Set.of(),List.of());
            assertEquals(core.get(1).origin(),layout.middleOrigin());
            assertEquals(core.get(1).bounds().maxY()+1,core.get(2).bounds().minY());
            count++;
        }
        assertEquals(672,count);
    }

    @Test void nativePorchesConnectViaAnExteriorScaffoldWhilePreservingWaterAndLoot() throws Exception {
        var templates=templates("bathhouse");
        var cases=new ArrayList<Map<String,Object>>();
        var used=new HashSet<String>();
        for (var rotation:Rotation.values()) for (int b=0;b<6;b++) for (int m=0;m<4;m++) {
            int t=(b+m+rotation.ordinal())%7;
            var sample=assemble(templates,b,m,t,rotation,(b+m+rotation.ordinal())%2==0);
            var model=new Model(sample);
            for (int exits=1;exits<=4;exits++) {
                model.added.clear();
                model.survey(exits);
                int expectedHeight=sample.pieces.get(0).template().size().getY() + (m==3?8:1);
                assertEquals(expectedHeight,model.added.size());
            }
            // A second survey can use the verified column without rewriting any original block.
            int placed=model.added.size();
            model.survey(4);
            assertEquals(placed,model.added.size());
            int loot=0,water=0,waterlogged=0;
            for (var p:sample.pieces) {
                used.add(p.template().id());
                for (var block:p.template().blocks()) {
                    boolean wet=block.props().getString("waterlogged").equals("true");
                    if (block.id().equals("minecraft:water")) water++;
                    else if (wet) waterlogged++;
                    else if (block.data().contains("LootTable")) loot++;
                    else if (!block.id().equals("minecraft:spawner")) continue;
                    var actual=model.placed.get(cell(p.at(block.pos())));
                    assertEquals(block.id(),actual.id());
                    assertEquals(block.props(),actual.props());
                    assertEquals(block.data(),actual.data());
                }
            }
            var row=new LinkedHashMap<String,Object>();
            row.put("rotation",rotation.toString()); row.put("stack",sample.pieces.subList(0,3).stream().map(p->p.template().id()).toList());
            row.put("spawners",sample.pieces.size()-3); row.put("scaffoldHeight",placed); row.put("sources",sample.sources.size());
            row.put("waterBlocks",water); row.put("waterloggedBlocks",waterlogged); row.put("lootContainers",loot); row.put("exitCounts",List.of(1,2,3,4));
            cases.add(row);
        }
        assertEquals(templates.keySet(),used);
        var report=new LinkedHashMap<String,Object>();
        report.put("wdaSha256",HASH); report.put("runtimeAccepted",false); report.put("stackRotations",672); report.put("terrainAdaptationMargin",12);
        report.put("scope","96 representative original assemblies on modeled grass, 1..4 exits each. Source, flowing and waterlogged blocks are checked before fluid simulation. The occupied top_3 chest-conflict socket is separately rejected.");
        report.put("cases",cases);
        var file=Path.of("build/reports/bathhouse-entry-audit.json"); Files.createDirectories(file.getParent());
        Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
    }

    @Test void rejectsLootChestConflictForEverySpawnerVariantAndRotation() throws Exception {
        var templates=templates("bathhouse");
        for (var rotation:Rotation.values()) {
            var sample=assemble(templates,2,3,3,rotation,false);
            var roof=sample.pieces.get(2);
            var socket=roof.template().blocks().stream().filter(b->b.pos().equals(new BlockPos(20,6,15))).findFirst().orElseThrow();
            var chest=cell(roof.at(socket.pos().above()));
            var original=new Terrain(sample.pieces).placed.get(chest);
            assertEquals("dungeons_arise:chests/bathhouse/bathhouse_normal",original.data().getString("LootTable"));
            for (int i=0;i<3;i++) {
                var all=new ArrayList<>(sample.pieces);
                all.add(connect(roof,socket,templates.get("spawner_"+i),Direction.DOWN));
                assertEquals("minecraft:air",new Terrain(all).placed.get(chest).id());
                assertThrows(IllegalArgumentException.class,()->BathhouseLayout.inspect(all.stream().map(Placed::piece).toList(),ENV,sample.sockets,sample.sources));
            }
        }
    }

    @Test void refusesIncompleteMisalignedOrDetachedPiecesAndMovesTheOriginalMarkers() throws Exception {
        var sample=assemble(templates("bathhouse"),2,3,0,Rotation.CLOCKWISE_90,true);
        var full=sample.graph();
        for (int i:List.of(0,1,2,3)) {
            var wrong=new ArrayList<>(full); var p=full.get(i);
            wrong.set(i,new WdaTemplatePiece(p.id(),p.origin().above(),p.rotation(),p.bounds().move(0,1,0),false));
            assertThrows(IllegalArgumentException.class,()->BathhouseLayout.inspect(wrong,ENV,sample.sockets,sample.sources));
        }
        assertThrows(IllegalArgumentException.class,()->BathhouseLayout.inspect(full.subList(0,2),ENV,sample.sockets,sample.sources));
        var wet=new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.WATER,4);
        assertThrows(IllegalArgumentException.class,()->BathhouseLayout.inspect(full,wet,sample.sockets,sample.sources));
        var layout=sample.layout(); var moved=layout.move(119,-28,-233);
        assertEquals(layout.at(31,9,15).move(119,-28,-233),moved.at(31,9,15));
        assertEquals(layout.waterSources().stream().map(p->p.offset(119,-28,-233)).toList(),moved.waterSources());
    }

    @Test void blockedPorchMissingSourceAndOccupiedScaffoldFailBeforeWrites() throws Exception {
        var templates=templates("bathhouse");
        var model=new Model(assemble(templates,2,3,3,Rotation.COUNTERCLOCKWISE_90,true));
        var bath=model.layout; var column=bath.at(31,9,15);
        var points=List.of(bath.at(25,9,15),bath.at(29,7,15),bath.at(27,8,14),bath.at(23,9,15),
                new Cell(column.x(),Y-1,column.z()),column.move(0,-2,0),column.move(0,1,0),
                cell(bath.waterSources().get(0)),model.reserved.perimeterSites(4).get(3));
        for (var p:points) {
            model.blocked.add(p);
            assertThrows(IllegalStateException.class,()->model.survey(4),"Block at "+p);
            assertTrue(model.added.isEmpty());
            model.blocked.remove(p);
        }
    }

    @Test void pinnedPoolsAndFinishMatchTheOriginalDefinition() throws Exception {
        assertEquals(HASH,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
        var templates=templates("bathhouse");
        assertEquals(20,templates.size());
        try (var zip=new ZipFile(JAR.toFile())) {
            String wg="data/dungeons_arise/worldgen/";
            var def=json(zip,wg+"structure/bathhouse.json");
            assertEquals("minecraft:jigsaw",def.get("type").getAsString());
            assertEquals("WORLD_SURFACE_WG",def.get("project_start_to_heightmap").getAsString());
            assertEquals("beard_box",def.get("terrain_adaptation").getAsString());
            assertEquals(0,def.getAsJsonObject("start_height").get("absolute").getAsInt());
            assertEquals("dungeons_arise:bathhouse/bathhouse_base",def.get("start_pool").getAsString());
            var rules=json(zip,wg+"processor_list/bathhouse_main.json").getAsJsonArray("processors");
            assertEquals(1,rules.size());
            assertEquals(JsonParser.parseString(BathhousePlanner.RULE),rules.get(0));
            var seen=new HashSet<String>();
            for (var pool:List.of("base","middle","top","spawners")) {
                var elements=json(zip,wg+"template_pool/bathhouse/bathhouse_"+pool+".json").getAsJsonArray("elements");
                for (var raw:elements) {
                    var entry=raw.getAsJsonObject(); var elem=entry.getAsJsonObject("element");
                    var id=elem.get("location").getAsString();
                    assertTrue(seen.add(id.substring(BathhouseLayout.PREFIX.length())));
                    assertEquals(BathhouseLayout.processors(id),elem.get("processors").getAsString());
                    assertEquals("minecraft:single_pool_element",elem.get("element_type").getAsString());
                    assertEquals("rigid",elem.get("projection").getAsString());
                    int weight=id.endsWith("spawner_0") || id.endsWith("spawner_1") ? 5 : 1;
                    assertEquals(weight,entry.get("weight").getAsInt());
                }
            }
            assertEquals(templates.keySet(),seen);
        }
        for (int i=0;i<3;i++) {
            var b=templates.get("spawner_"+i).blocks().stream().filter(p->p.id().equals("minecraft:spawner")).findFirst().orElseThrow();
            assertEquals(List.of("minecraft:husk","minecraft:skeleton","minecraft:frog").get(i),
                    b.data().getCompound("SpawnData").getCompound("entity").getString("id"));
        }
    }
}
