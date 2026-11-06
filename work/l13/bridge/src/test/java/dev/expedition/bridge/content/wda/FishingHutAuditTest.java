package dev.expedition.bridge.content.wda;

import com.google.gson.GsonBuilder;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.DungeonSpec;
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

class FishingHutAuditTest {
    private static final BlockPos ORIGIN = new BlockPos(-117,Y-3,231);
    private static WdaStructurePlanner.Environment env(IslandPlan.Surface surface,int depth,boolean boarding) {
        return new WdaStructurePlanner.Environment(Y,8,6,16,surface,depth,boarding);
    }
    private static final List<WdaStructurePlanner.Environment> SITES = List.of(env(IslandPlan.Surface.GRASS,0,false),
            env(IslandPlan.Surface.ROCK,0,false), env(IslandPlan.Surface.WATER,2,false), env(IslandPlan.Surface.WATER,2,true),
            env(IslandPlan.Surface.WATER,3,false), env(IslandPlan.Surface.WATER,3,true));

    private static List<Placed> assemble(Map<String,Template> t,Rotation rotation,boolean spawn) {
        var root = new Placed(t.get("main"), ORIGIN, rotation);
        if (!spawn) return List.of(root);
        return List.of(root, connect(root,root.template().joint("spawner",Direction.UP),t.get("spawner_0"),Direction.DOWN));
    }

    private static class Model extends Terrain implements FishingHutRoute.Probe {
        final IslandPlan site;
        final FishingHutLayout hut;
        final Set<Cell> additions = new HashSet<>();
        Model(List<Placed> pieces,WdaStructurePlanner.Environment env) {
            super(pieces);
            site = new IslandPlan(island.building(),Y,env.padding(),env.rim(),env.foundationDepth(),env.surface(),env.waterDepth());
            hut = FishingHutLayout.inspect(pieces.stream().map(Placed::piece).toList(),env);
        }
        boolean has(Cell p,String name) {
            return !blocked.contains(p) && placed.containsKey(p) && placed.get(p).id().equals("minecraft:"+name);
        }
        public boolean oakPlanks(Cell p) { return !blocked.contains(p) && (additions.contains(p) || has(p,"oak_planks")); }
        public boolean sprucePile(Cell p) { return has(p,"spruce_log"); }
        public boolean barrel(Cell p) { return has(p,"barrel"); }
        public boolean originalEncounter(Cell socket,boolean hasSpawner) {
            if (hasSpawner ? !has(socket,"spawner") : at(socket)!=Type.AIR) return false;
            for (int dy=1;dy<=2;dy++) {
                var p = socket.move(0,dy,0);
                if (!has(p,"chain") || !placed.get(p).props().getString("axis").equals("y")
                        || !placed.get(p).props().getString("waterlogged").equals("false")) return false;
            }
            return true;
        }
        public boolean waterSource(Cell p) {
            var water=site.fluidColumn(p.x(),p.z());
            return water!=null && p.y()>=water.bottom() && p.y()<=water.top() && !blocked.contains(p)
                    && !placed.containsKey(p) && !additions.contains(p);
        }
        @Override public Type at(Cell p) {
            if (blocked.contains(p)) return Type.BLOCKED;
            if (additions.contains(p)) return Type.SUPPORT;
            if (placed.containsKey(p)) return super.at(p);
            if (waterSource(p)) return Type.BLOCKED;
            var land=site.solidColumn(p.x(),p.z());
            return land!=null && p.y()>=land.bottom() && p.y()<=land.top() ? Type.SUPPORT : Type.AIR;
        }
        public void bridgeSource(Cell p) {
            assertFalse(site.building().contains(p.x(),p.y(),p.z()));
            assertTrue(waterSource(p));
            assertTrue(additions.add(p));
        }
        void run(int exits) {
            var route=new FishingHutRoute(hut,site,exits,this);
            for (int i=0;i<12000;i++) if (route.step()) return;
            fail("Dry bank survey exceeded the island area");
        }
    }

    @Test void bothEncounterLayoutsReachTheRoomFromDryLandOrShallowWater() throws Exception {
        var templates=templates("fishing_hut");
        var rows=new ArrayList<Map<String,Object>>();
        for (var rot:Rotation.values()) for (boolean spawn:List.of(false,true)) for (var env:SITES) {
            var pieces=assemble(templates,rot,spawn);
            var m=new Model(pieces,env);
            for (int exits=1;exits<=4;exits++) {
                m.additions.clear(); m.run(exits);
                assertEquals(env.boarding()?1:0,m.additions.size());
                // Native legacy pieces ignore template air. The column below the dock keeps its original background.
                var underDock=m.hut.at(0,1,10);
                assertFalse(m.placed.containsKey(underDock));
                if (env.waterDepth()==0) assertEquals(Type.SUPPORT,m.at(underDock));
                else assertTrue(m.waterSource(underDock));
                if (env.waterDepth()>0) {
                    long open=List.of(10,11).stream().filter(z->m.waterSource(m.hut.at(-1,2,z))).count();
                    assertEquals(env.boarding()?1:2,open);
                    for(int z:List.of(10,11)) assertTrue(m.waterSource(m.hut.at(-1,1,z)));
                }
                if (env.boarding()) {
                    var first=Set.copyOf(m.additions);
                    m.run(exits);
                    assertEquals(first,m.additions);
                }
            }
            for (var p:List.of(m.hut.at(8,3,8),m.hut.at(8,4,8),m.hut.at(9,3,8))) {
                assertEquals("dungeons_arise:chests/fishing_hut/fishing_hut_barrels",m.placed.get(p).data().getString("LootTable"));
            }
            if (spawn) {
                var actual=m.placed.get(m.hut.at(10,5,5));
                var source=templates.get("spawner_0").blocks().stream().filter(b->b.id().equals("minecraft:spawner")).findFirst().orElseThrow();
                assertEquals(source.data(),actual.data());
                var zombie=actual.data().getCompound("SpawnData").getCompound("entity");
                assertEquals("minecraft:zombie",zombie.getString("id"));
                assertEquals("minecraft:fishing_rod",zombie.getList("HandItems",Tag.TAG_COMPOUND).getCompound(0).getString("id"));
            }
            var row=new LinkedHashMap<String,Object>();
            row.put("rotation",rot.toString()); row.put("spawner",spawn); row.put("surface",env.surface().toString());
            row.put("waterDepth",env.waterDepth()); row.put("boarding",env.boarding()); row.put("addedPlanks",m.additions.size());
            row.put("originalBarrels",3); row.put("exitCounts",List.of(1,2,3,4)); rows.add(row);
        }
        assertEquals(48,rows.size());
        var report=new LinkedHashMap<String,Object>();
        report.put("wdaSha256",HASH); report.put("runtimeAccepted",false);
        report.put("scope","One original hut and optional aligned zombie spawner. Four rotations in six environments with 1..4 exits; swim, fishing combat and fluid updates require in-game testing.");
        report.put("cases",rows);
        var file=Path.of("build/reports/fishing-hut-entry-audit.json"); Files.createDirectories(file.getParent());
        Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
    }

    @Test void obstructedLaneUsesTheOtherOriginalDockWithoutReplacingAnObstacle() throws Exception {
        var pieces=assemble(templates("fishing_hut"),Rotation.COUNTERCLOCKWISE_90,true);
        for (int z:List.of(10,11)) {
            for (var env:SITES) {
                var m=new Model(pieces,env);
                m.blocked.add(m.hut.at(0,4,z));
                m.run(4);
                assertEquals(env.boarding()?Set.of(m.hut.at(-1,2,21-z)):Set.of(),m.additions);
            }
        }
        var water=new Model(pieces,SITES.get(3));
        water.blocked.add(water.hut.at(-1,2,10));
        water.run(4);
        assertEquals(Set.of(water.hut.at(-1,2,11)),water.additions);
    }

    @Test void refusesDamagedPilesBarrelsRoomAndSealedWaterApproachesBeforeWriting() throws Exception {
        var m=new Model(assemble(templates("fishing_hut"),Rotation.CLOCKWISE_90,true),SITES.get(5));
        var h=m.hut;
        for (var p:List.of(h.at(7,-1,2),h.at(16,0,14),h.at(8,4,8),h.at(9,3,9),h.at(10,4,8),
                h.at(10,5,5),h.at(10,6,5),m.site.perimeterSites(4).get(3))) {
            m.blocked.add(p);
            assertThrows(IllegalStateException.class,()->m.run(4),"Obstruction "+p);
            assertTrue(m.additions.isEmpty());
            m.blocked.remove(p);
        }
        for (int y:List.of(1,2,3,4)) {
            m.blocked.add(h.at(-1,y,10)); m.blocked.add(h.at(-1,y,11));
            assertThrows(IllegalStateException.class,()->m.run(1));
            assertTrue(m.additions.isEmpty()); m.blocked.clear();
        }
    }

    @Test void validatesAlignmentDepthAndRelocatedLandmarks() throws Exception {
        var pieces=assemble(templates("fishing_hut"),Rotation.CLOCKWISE_180,true).stream().map(Placed::piece).toList();
        var root=pieces.get(0); var child=pieces.get(1);
        for (int depth:List.of(4,12,64)) assertThrows(IllegalArgumentException.class,
                ()->FishingHutLayout.inspect(pieces,env(IslandPlan.Surface.WATER,depth,false)));
        var wrongMode=new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.ROCK,0,false,DungeonSpec.EntryMode.START_ROOM);
        assertThrows(IllegalArgumentException.class,()->FishingHutLayout.inspect(pieces,wrongMode));
        for (var wrong:List.of(new WdaTemplatePiece(child.id(),child.origin().east(),child.rotation(),child.bounds().move(1,0,0),false),
                new WdaTemplatePiece(child.id(),child.origin(),Rotation.NONE,child.bounds(),false),
                new WdaTemplatePiece(child.id(),child.origin(),child.rotation(),child.bounds(),true))) {
            assertThrows(IllegalArgumentException.class,()->FishingHutLayout.inspect(List.of(root,wrong),SITES.get(0)));
        }
        assertThrows(IllegalArgumentException.class,()->FishingHutLayout.inspect(List.of(root,child,child),SITES.get(0)));
        assertThrows(IllegalArgumentException.class,()->FishingHutLayout.inspect(List.of(new WdaTemplatePiece(root.id(),root.origin().above(),
                root.rotation(),root.bounds().move(0,1,0),false)),SITES.get(0)));
        var hut=FishingHutLayout.inspect(pieces,SITES.get(3)); var moved=hut.move(201,-21,-198);
        assertEquals(hut.at(-1,2,10).move(201,-21,-198),moved.at(-1,2,10));
        assertTrue(moved.hasSpawner()); assertTrue(moved.boarding());
    }

    @Test void pinnedBeachDefinitionAndBothRigidPoolsUseTheOriginalJoint() throws Exception {
        assertEquals(HASH,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
        var templates=templates("fishing_hut");
        assertEquals(Set.of("main","spawner_0"),templates.keySet());
        var root=templates.get("main"); var child=templates.get("spawner_0");
        for (var b:List.of(root.joint("spawner",Direction.UP),child.joint("spawner",Direction.DOWN))) {
            assertEquals("aligned",b.data().getString("joint"));
            assertEquals("minecraft:structure_void",b.data().getString("final_state"));
            assertEquals("dungeons_arise:spawner",b.data().getString("name"));
            assertEquals("dungeons_arise:prairie/fishing_hut/fishing_hut_spawners",b.data().getString("pool"));
        }
        try (var zip=new ZipFile(JAR.toFile())) {
            String wg="data/dungeons_arise/worldgen/";
            var def=json(zip,wg+"structure/fishing_hut.json");
            assertEquals("minecraft:jigsaw",def.get("type").getAsString());
            assertEquals("WORLD_SURFACE_WG",def.get("project_start_to_heightmap").getAsString());
            assertEquals(-2,def.getAsJsonObject("start_height").get("absolute").getAsInt());
            assertEquals("none",def.has("terrain_adaptation")?def.get("terrain_adaptation").getAsString():"none");
            assertEquals("dungeons_arise:prairie/fishing_hut/fishing_hut_main",def.get("start_pool").getAsString());
            assertEquals("#dungeons_arise:has_structure/fishing_hut_biomes",def.get("biomes").getAsString());
            var biomes=json(zip,"data/dungeons_arise/tags/worldgen/biome/has_structure/fishing_hut_biomes.json").getAsJsonArray("values");
            assertEquals(1,biomes.size()); assertEquals("#minecraft:is_beach",biomes.get(0).getAsString());
            for (var template:List.of(root,child)) {
                String id=template==root?"main":"spawners";
                var entries=json(zip,wg+"template_pool/prairie/fishing_hut/fishing_hut_"+id+".json").getAsJsonArray("elements");
                assertEquals(1,entries.size());
                var entry=entries.get(0).getAsJsonObject(); var element=entry.getAsJsonObject("element");
                assertEquals(template.resource(),element.get("location").getAsString());
                assertEquals(1,entry.get("weight").getAsInt()); assertEquals("rigid",element.get("projection").getAsString());
                assertEquals("minecraft:empty",element.get("processors").getAsString());
                assertEquals("minecraft:legacy_single_pool_element",element.get("element_type").getAsString());
            }
        }
    }
}
