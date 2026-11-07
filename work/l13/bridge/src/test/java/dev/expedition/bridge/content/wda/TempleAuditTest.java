package dev.expedition.bridge.content.wda;

import com.google.gson.GsonBuilder;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import static dev.expedition.bridge.content.wda.WdaTemplateFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class TempleAuditTest {
    private static final BlockPos ORIGIN=new BlockPos(-113,Y-3,221);
    private static final String POOL="dungeons_arise:fortified/abandoned_temple/";
    private static final WdaStructurePlanner.Environment ENV=new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.GRASS);

    private static List<Placed> assemble(Map<String,Template> templates,int variant,Rotation rotation,int roadType,int decoration) {
        var root=new Placed(templates.get(""+variant),ORIGIN,rotation);
        var pieces=new ArrayList<>(List.of(root));
        var roadSocket=root.template().blocks().stream().filter(b->b.pos().equals(variant==0?new BlockPos(15,0,30):new BlockPos(0,0,15))).findFirst().orElseThrow();
        var road=connect(root,roadSocket,templates.get(roadType==2?"abandoned_road_entrance":"abandoned_road_"+roadType),Direction.NORTH);
        pieces.add(road);
        if (variant==0) {
            var inner=root.template().blocks().stream().filter(b->b.pos().equals(new BlockPos(15,0,26))).findFirst().orElseThrow();
            pieces.add(connect(root,inner,templates.get("abandoned_road_entrance"),Direction.NORTH));
        }
        if (roadType==0) {
            var outgoing=road.template().joint("abandoned_temple_road",Direction.SOUTH);
            pieces.add(connect(road,outgoing,templates.get("abandoned_road_1"),Direction.NORTH));
        }
        int turns=decoration;
        for (var parent:List.copyOf(pieces)) for (var socket:parent.template().blocks()) {
            String pool=socket.data().getString("pool");
            if (pool.equals(POOL+"abandoned_temple_deco")) {
                pieces.add(connect(parent,socket,templates.get("deco_"+decoration),Direction.DOWN));
            } else if (pool.equals(POOL+"enemies")) {
                String id=switch(socket.data().getString("target")) {
                    case "dungeons_arise:illusioner" -> "illusioner_normal_0";
                    case "dungeons_arise:armored" -> "skeleton_armored_0";
                    case "dungeons_arise:armored_iron" -> "skeleton_armored_1";
                    default -> throw new IllegalStateException("Unknown native encounter");
                };
                pieces.add(connect(parent,socket,templates.get(id),Direction.DOWN,Rotation.values()[(turns++)%4]));
            }
        }
        return pieces;
    }

    private static final class Model extends Terrain implements TempleRoute.Probe {
        final TempleLayout temple;
        final IslandPlan reserved;
        final Set<Cell> added=new HashSet<>();
        Model(List<Placed> parts,IslandPlan.Surface surface) {
            super(parts);
            temple=TempleLayout.inspect(parts.stream().map(Placed::piece).toList(),new WdaStructurePlanner.Environment(Y,8,6,16,surface));
            reserved=new IslandPlan(island.building().expand(12),Y,8,6,16,surface);
        }
        @Override public Type at(Cell p) { return added.contains(p)?Type.BLOCKED:super.at(p); }
        boolean has(Cell p,String id) { return !blocked.contains(p) && placed.containsKey(p) && placed.get(p).id().equals("minecraft:"+id); }
        public boolean stair(Cell p,Direction facing) {
            if (!has(p,"stone_brick_stairs")) return false;
            var s=placed.get(p);
            return s.facing()==facing && s.props().getString("half").equals("bottom")
                    && s.props().getString("shape").equals("straight") && s.props().getString("waterlogged").equals("false");
        }
        public boolean gravel(Cell p) { return has(p,"gravel") && at(p.move(0,-1,0))==Type.SUPPORT; }
        public boolean chest(Cell p,String tier) {
            return has(p,"chest") && placed.get(p).data().getString("LootTable").equals("dungeons_arise:chests/abandoned_temple/abandoned_temple_"+tier);
        }
        public boolean scaffold(Cell p) { return !blocked.contains(p) && added.contains(p); }
        public void addScaffold(Cell p) {
            if (scaffold(p)) return;
            assertEquals(Type.AIR,at(p));
            var below=p.move(0,-1,0);
            assertTrue(at(below)==Type.SUPPORT || scaffold(below));
            assertFalse(placed.containsKey(p),"Original template cell must not be replaced");
            added.add(p);
        }
        void survey(int exits) {
            var route=new TempleRoute(temple,reserved,exits,this);
            for (int i=0;i<50000;i++) if (route.step()) return;
            fail("Temple route exceeded model budget");
        }
    }

    @Test void bothHallsRetainOriginalContentsAndConnectEveryExit() throws Exception {
        var templates=templates("abandoned_temple");
        var cases=new ArrayList<Map<String,Object>>(); var seen=new HashSet<String>();
        for (var surface:List.of(IslandPlan.Surface.GRASS,IslandPlan.Surface.ROCK)) for (int variant=0;variant<=1;variant++)
            for (var rotation:Rotation.values()) for (int road=0;road<3;road++) for (int deco=0;deco<3;deco++) {
                var parts=assemble(templates,variant,rotation,road,deco);
                var model=new Model(parts,surface);
                for (int exits=1;exits<=4;exits++) { model.added.clear(); model.survey(exits); assertEquals(variant==0?0:5,model.added.size()); }
                model.survey(4); assertEquals(variant==0?0:5,model.added.size());
                int loot=0,entities=0;
                for (var part:parts) {
                    seen.add(part.template().id()); entities+=part.template().entities().size();
                    for (var block:part.template().blocks()) if (block.data().contains("LootTable")) {
                        var p=part.at(block.pos()); loot++;
                        assertEquals(block.data(),model.placed.get(new Cell(p.getX(),p.getY(),p.getZ())).data());
                    }
                }
                assertEquals(4,loot); assertTrue(entities>0);
                var row=new LinkedHashMap<String,Object>();
                row.put("surface",surface); row.put("variant",variant); row.put("rotation",rotation); row.put("road",road); row.put("decoration",deco);
                row.put("pieces",parts.size()); row.put("nativeEntities",entities); row.put("lootChests",loot);
                row.put("scaffoldBlocks",model.added.size()); row.put("exitCounts",List.of(1,2,3,4)); cases.add(row);
            }
        assertEquals(144,cases.size()); assertEquals(templates.keySet(),seen);
        var report=new LinkedHashMap<String,Object>();
        report.put("wdaSha256",HASH); report.put("runtimeAccepted",false); report.put("terrainAdaptationMargin",12);
        report.put("scope","144 source assemblies, both halls, all road/decor templates, four rotations and dry grass/rock. All requested exits, original loot and modeled native inhabitants. Actual random graph, scaffold physics, combat and persistence pending.");
        report.put("cases",cases);
        var p=Path.of("build/reports/temple-entry-audit.json"); Files.createDirectories(p.getParent());
        Files.writeString(p,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
    }

    @Test void eitherSideCanServeTheRaisedHallAndWritesWaitForACompleteSurvey() throws Exception {
        var m=new Model(assemble(templates("abandoned_temple"),1,Rotation.CLOCKWISE_90,0,2),IslandPlan.Surface.GRASS);
        for (int x:List.of(3,27)) {
            var blocked=m.temple.at(x,5,15);
            m.blocked.add(blocked); m.survey(4);
            int other=x==3?27:3;
            for (int y=3;y<=7;y++) assertTrue(m.added.contains(m.temple.at(other,y,15)));
            m.added.clear(); m.blocked.clear();
        }
        m.blocked.add(m.temple.at(3,5,15)); m.blocked.add(m.temple.at(27,5,15));
        assertThrows(IllegalStateException.class,()->m.survey(4)); assertTrue(m.added.isEmpty());
        m.blocked.clear(); m.blocked.add(m.reserved.perimeterSites(4).get(3));
        assertThrows(IllegalStateException.class,()->m.survey(4)); assertTrue(m.added.isEmpty());
    }

    @Test void damagedOriginalStairsHeadroomAndLootFailBeforeAddingBlocks() throws Exception {
        var t=templates("abandoned_temple");
        var low=new Model(assemble(t,0,Rotation.COUNTERCLOCKWISE_90,1,0),IslandPlan.Surface.GRASS);
        for (var p:List.of(low.temple.at(15,2,23),low.temple.at(12,5,25),low.temple.at(15,6,20),low.temple.at(9,5,23))) {
            low.blocked.add(p); assertThrows(IllegalStateException.class,()->low.survey(4)); low.blocked.clear();
        }
        var high=new Model(assemble(t,1,Rotation.NONE,1,1),IslandPlan.Surface.ROCK);
        high.blocked.add(high.temple.at(15,26,12));
        assertThrows(IllegalStateException.class,()->high.survey(4)); assertTrue(high.added.isEmpty());
    }

    @Test void rootAndProjectionAreValidatedAndMarkersRelocateTogether() throws Exception {
        var graph=assemble(templates("abandoned_temple"),0,Rotation.CLOCKWISE_180,0,2).stream().map(Placed::piece).toList();
        var temple=TempleLayout.inspect(graph,ENV); var moved=temple.move(47,12,-211);
        assertEquals(temple.at(15,5,18).move(47,12,-211),moved.at(15,5,18));
        for (int index:List.of(0,1)) {
            var wrong=new ArrayList<>(graph); var p=wrong.get(index);
            wrong.set(index,new WdaTemplatePiece(p.id(),p.origin(),p.rotation(),p.bounds(),!p.terrainMatching()));
            assertThrows(IllegalArgumentException.class,()->TempleLayout.inspect(wrong,ENV));
            wrong.set(index,new WdaTemplatePiece(p.id(),p.origin().above(),p.rotation(),p.bounds().move(0,1,0),p.terrainMatching()));
            assertThrows(IllegalArgumentException.class,()->TempleLayout.inspect(wrong,ENV));
        }
        var duplicate=new ArrayList<>(graph); duplicate.add(graph.get(0));
        assertThrows(IllegalArgumentException.class,()->TempleLayout.inspect(duplicate,ENV));
        assertThrows(IllegalArgumentException.class,()->TempleLayout.inspect(graph,new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.WATER,2)));
    }

    @Test void projectedRoadSurfaceIsOneBlockBelowTheRigidAttachmentInlets() throws Exception {
        var parts=assemble(templates("abandoned_temple"),0,Rotation.CLOCKWISE_90,0,2);
        var model=new Model(parts,IslandPlan.Surface.GRASS);
        for (var p:parts) if (p.template().projected()) {
            assertEquals(Y,p.origin().getY());
            for (var block:p.template().blocks()) {
                var n=p.at(block.pos());
                var actual=model.placed.get(new Cell(n.getX(),Y-1,n.getZ()));
                assertNotNull(actual);
                if (!block.id().equals("minecraft:jigsaw")) assertEquals(block.id(),actual.id());
            }
        }
        for (var p:parts) if (p.template().id().startsWith("deco_")) assertEquals(Y,p.origin().getY());
    }

    @Test void pinnedPoolsKeepNativeContentsAndFullStructureStraySpawns() throws Exception {
        assertEquals(HASH,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
        var t=templates("abandoned_temple"); assertEquals(11,t.size());
        try (var zip=new ZipFile(JAR.toFile())) {
            var def=json(zip,"data/dungeons_arise/worldgen/structure/abandoned_temple.json");
            assertEquals(POOL+"abandoned_temple_start",def.get("start_pool").getAsString()); assertEquals("minecraft:jigsaw",def.get("type").getAsString());
            assertEquals("beard_thin",def.get("terrain_adaptation").getAsString());
            assertEquals(-2,def.getAsJsonObject("start_height").get("absolute").getAsInt());
            assertEquals("WORLD_SURFACE_WG",def.get("project_start_to_heightmap").getAsString());
            var spawn=def.getAsJsonObject("spawn_overrides").getAsJsonObject("monster");
            assertEquals("full",spawn.get("bounding_box").getAsString());
            assertEquals("minecraft:stray",spawn.getAsJsonArray("spawns").get(0).getAsJsonObject().get("type").getAsString());
            for (var pool:List.of("abandoned_temple_start","abandoned_temple_roads","abandoned_temple_deco","enemies")) {
                var elements=json(zip,"data/dungeons_arise/worldgen/template_pool/fortified/abandoned_temple/"+pool+".json").getAsJsonArray("elements");
                assertEquals(pool.endsWith("start")?2:3,elements.size());
                for (var raw:elements) {
                    var entry=raw.getAsJsonObject(); var el=entry.getAsJsonObject("element"); var id=el.get("location").getAsString();
                    assertEquals("minecraft:empty",el.get("processors").getAsString()); assertEquals("minecraft:single_pool_element",el.get("element_type").getAsString());
                    assertEquals(pool.endsWith("roads")?"terrain_matching":"rigid",el.get("projection").getAsString());
                    assertEquals(id.endsWith("abandoned_road_0")?4:1,entry.get("weight").getAsInt());
                }
            }
        }
        for (var id:List.of("illusioner_normal_0","skeleton_armored_0","skeleton_armored_1")) {
            var e=t.get(id).entities(); assertEquals(1,e.size());
            var n=e.getCompound(0).getCompound("nbt");
            assertEquals(id.startsWith("illusioner")?"minecraft:illusioner":"minecraft:skeleton",n.getString("id"));
            boolean illusioner=id.startsWith("illusioner");
            assertEquals(!illusioner,n.getBoolean("PersistenceRequired"));
            assertEquals(illusioner?32:20,n.getFloat("Health"));
            var hands=n.getList("HandItems",Tag.TAG_COMPOUND);
            assertEquals(2,hands.size());
            for (int i=0;i<hands.size();i++) assertTrue(hands.getCompound(i).isEmpty());
            var armor=n.getList("ArmorItems",Tag.TAG_COMPOUND);
            assertEquals(4,armor.size());
            var slots=List.of("boots","leggings","chestplate","helmet");
            for (int i=0;i<armor.size();i++) {
                if (illusioner) assertTrue(armor.getCompound(i).isEmpty());
                else assertEquals("minecraft:"+(id.endsWith("_0")?"chainmail_":"iron_")+slots.get(i),armor.getCompound(i).getString("id"));
            }
        }
    }
}
