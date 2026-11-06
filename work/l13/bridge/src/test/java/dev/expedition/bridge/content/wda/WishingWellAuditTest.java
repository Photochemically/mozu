package dev.expedition.bridge.content.wda;

import com.google.gson.GsonBuilder;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
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

class WishingWellAuditTest {
    private static final BlockPos ORIGIN = new BlockPos(-97,Y-8,231);
    private static final String POOL = WishingWellLayout.POOL;
    private static final Map<Integer,String> EFFECTS = Map.of(1,"speed",10,"regeneration",20,"wither",11,"resistance",3,"haste");
    private static final List<IslandPlan.Surface> SURFACES = List.of(IslandPlan.Surface.GRASS,IslandPlan.Surface.ROCK);

    private static List<Placed> assemble(Map<String,Template> templates,Rotation rotation,String child,Rotation childRotation) {
        var root = new Placed(templates.get(child.isEmpty() ? "0_no_effects" : "0"),ORIGIN,rotation);
        if (child.isEmpty()) return List.of(root);
        var socket = root.template().joint("wishing_well_effects",Direction.UP);
        var connected = connect(root,socket,templates.get(child),Direction.DOWN,childRotation);
        assertEquals(childRotation,connected.rotation());
        return List.of(root,connected);
    }

    private static final class Model extends Terrain implements WishingWellRoute.Probe {
        final IslandPlan reserved;
        final WishingWellLayout layout;
        final List<WishingWellLayout.Cloud> clouds = new ArrayList<>();
        Model(List<Placed> pieces,IslandPlan.Surface surface) {
            super(pieces);
            reserved = new IslandPlan(island.building().expand(12),Y,8,6,16,surface);
            var root = pieces.get(0).template();
            var water = root.blocks().stream().filter(b->b.id().equals("minecraft:water") && b.props().getString("level").equals("0"))
                    .map(Block::pos).toList();
            layout = WishingWellLayout.inspect(root.pools().iterator().next(),pieces.stream().map(Placed::piece).toList(),
                    new WdaStructurePlanner.Environment(Y,8,6,16,surface),water);
            for (var p : pieces) for (int i=0;i<p.template().entities().size();i++) {
                var entity = p.template().entities().getCompound(i);
                var nbt = entity.getCompound("nbt");
                assertEquals("minecraft:area_effect_cloud",nbt.getString("id"));
                var coords = entity.getList("pos",Tag.TAG_DOUBLE);
                var local = BlockPos.containing(coords.getDouble(0),coords.getDouble(1),coords.getDouble(2));
                // Non-integral X/Z stay inside their rotated template cell for all four rotations.
                var position = p.at(local);
                var nativeEffects = nbt.getList("Effects",Tag.TAG_COMPOUND);
                assertEquals(1,nativeEffects.size());
                String status = EFFECTS.get(nativeEffects.getCompound(0).getInt("Id"));
                clouds.add(new WishingWellLayout.Cloud(position,p.origin().getY()+coords.getDouble(1)+0.5,
                        !nbt.contains("Potion"),"minecraft:"+status,nbt));
            }
        }
        boolean has(Cell p,String id) {
            return !blocked.contains(p) && placed.containsKey(p) && placed.get(p).id().equals("minecraft:"+id);
        }
        public boolean grass(Cell p) { return has(p,"grass"); }
        public boolean rim(Cell p,Direction facing) {
            if (!has(p,"stone_brick_stairs")) return false;
            var state = placed.get(p);
            return state.facing()==facing && state.props().getString("half").equals("top")
                    && state.props().getString("shape").equals("straight") && state.props().getString("waterlogged").equals("false");
        }
        public boolean source(BlockPos pos) {
            var cell=new Cell(pos.getX(),pos.getY(),pos.getZ());
            return has(cell,"water") && placed.get(cell).props().getString("level").equals("0");
        }
        public boolean fixtures(WishingWellLayout w) {
            var cauldron=w.at(7,11,7); var lantern=w.at(7,7,7);
            if (!has(cauldron,"water_cauldron") || !placed.get(cauldron).props().getString("level").equals("3")
                    || !has(lantern,"soul_lantern") || !placed.get(lantern).props().getString("hanging").equals("true")) return false;
            for (var c:List.of(w.at(7,8,7),w.at(7,9,7))) if (!has(c,"chain") || !placed.get(c).props().getString("axis").equals("y")) return false;
            return w.effect()==WishingWellLayout.Effect.NONE || has(w.at(7,1,7),"gravel");
        }
        void survey(int exits) {
            if (!layout.matchesClouds(clouds)) throw new IllegalStateException("Invalid native cloud");
            var route = new WishingWellRoute(layout,reserved,exits,this);
            for (int i=0;i<12000;i++) if (route.step()) return;
            fail("Disconnected route or exceeded budget");
        }
    }

    @Test void allOriginalEffectsAndPlainRootReachTheRim() throws Exception {
        var templates = templates("wishing_well");
        var cases = new ArrayList<Map<String,Object>>();
        for (var surface : SURFACES) for (var r : Rotation.values()) for (var effect : WishingWellLayout.Effect.values()) {
            var rotations = effect==WishingWellLayout.Effect.NONE ? List.of(Rotation.NONE) : List.of(Rotation.values());
            for (var child : rotations) {
                var m = new Model(assemble(templates,r,effect.piece,child),surface);
                assertEquals(effect,m.layout.effect());
                assertEquals(19,m.layout.sourceOffsets().size());
                assertEquals(effect==WishingWellLayout.Effect.NONE ? 0 : 1,m.clouds.size());
                for (int exits=1;exits<=4;exits++) m.survey(exits);
                var row = new LinkedHashMap<String,Object>();
                row.put("surface",surface); row.put("rotation",r);
                row.put("startPool",effect==WishingWellLayout.Effect.NONE ? POOL+"_no_effects" : POOL);
                row.put("childRotation",effect==WishingWellLayout.Effect.NONE ? "absent" : child.name());
                row.put("effect",effect); row.put("sources",m.layout.sourceOffsets().size());
                row.put("exitCounts",List.of(1,2,3,4)); cases.add(row);
            }
        }
        assertEquals(168,cases.size());
        var report = new LinkedHashMap<String,Object>();
        report.put("wdaSha256",HASH); report.put("runtimeAccepted",false); report.put("terrainAdaptationMargin",12);
        report.put("scope","160 original effect assemblies and 8 explicit no-effects pool cases. Models dry grass/rock, native slopes and entity data before fluid simulation. Server tracking, status application and multiplayer pending.");
        report.put("cases",cases);
        var path=Path.of("build/reports/wishing-well-entry-audit.json"); Files.createDirectories(path.getParent());
        Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
    }

    @Test void eachLaneIndependentlyWorksAndBlockedSlopesOrExitsFail() throws Exception {
        var m = new Model(assemble(templates("wishing_well"),Rotation.CLOCKWISE_90,"effect_2_wither",Rotation.NONE),SURFACES.get(0));
        var rims = new ArrayList<Cell>();
        for (int i=6;i<=8;i++) { rims.add(m.layout.at(11,10,i)); rims.add(m.layout.at(i,10,11)); }
        m.blocked.addAll(rims);
        assertThrows(IllegalStateException.class,()->m.survey(4));
        for (var rim:rims) {
            m.blocked.remove(rim); m.survey(4); m.blocked.add(rim);
        }
        m.blocked.clear();
        // Block all take-off columns at jump height, leaving both feet and head space clear.
        for (int i=6;i<=8;i++) { m.blocked.add(m.layout.at(15,11,i)); m.blocked.add(m.layout.at(i,11,14)); }
        assertThrows(IllegalStateException.class,()->m.survey(4));
        m.blocked.clear(); m.blocked.add(m.reserved.perimeterSites(4).get(3));
        assertThrows(IllegalStateException.class,()->m.survey(4));
    }

    @Test void alteredBasinFixturesAndCloudsCannotReleaseTheEntrance() throws Exception {
        var templates = templates("wishing_well");
        var m = new Model(assemble(templates,Rotation.NONE,"effect_2_wither",Rotation.CLOCKWISE_90),SURFACES.get(0));
        var source = m.layout.sourceOffsets().get(0);
        for (var c:List.of(m.layout.at(source.getX(),1,source.getZ()),m.layout.at(7,11,7),m.layout.at(7,7,7),m.layout.at(7,8,7),m.layout.at(7,1,7))) {
            m.blocked.add(c); assertThrows(IllegalStateException.class,()->m.survey(4)); m.blocked.clear();
        }
        var cloud=m.clouds.remove(0); assertThrows(IllegalStateException.class,()->m.survey(4));
        assertEquals(Y-4.5,cloud.top());
        assertFalse(m.layout.matchesClouds(List.of(cloud,cloud)));
        assertFalse(m.layout.matchesClouds(List.of(new WishingWellLayout.Cloud(cloud.position().above(),cloud.top(),true,cloud.status(),cloud.data()))));
        assertFalse(m.layout.matchesClouds(List.of(new WishingWellLayout.Cloud(cloud.position(),Y,true,cloud.status(),cloud.data()))));
        assertFalse(m.layout.matchesClouds(List.of(new WishingWellLayout.Cloud(cloud.position(),cloud.top(),true,"minecraft:speed",cloud.data()))));
        var changed=cloud.data().copy(); changed.putFloat("Radius",4);
        assertFalse(m.layout.matchesClouds(List.of(new WishingWellLayout.Cloud(cloud.position(),cloud.top(),true,cloud.status(),changed))));
        var plain = new Model(assemble(templates,Rotation.NONE,"",Rotation.NONE),SURFACES.get(0));
        plain.clouds.add(cloud); assertThrows(IllegalStateException.class,()->plain.survey(1));
    }

    @Test void wrongPoolMissingChildAndShiftedFramesAreRejected() throws Exception {
        var t=templates("wishing_well"); var parts=assemble(t,Rotation.CLOCKWISE_180,"effect_2_wither",Rotation.CLOCKWISE_90);
        var graph=parts.stream().map(Placed::piece).toList(); var layout=new Model(parts,SURFACES.get(1)).layout;
        var env=new WdaStructurePlanner.Environment(Y,8,6,16,SURFACES.get(1)); var water=layout.sourceOffsets();
        var plain=assemble(t,Rotation.NONE,"",Rotation.NONE).stream().map(Placed::piece).toList();
        assertThrows(IllegalArgumentException.class,()->WishingWellLayout.inspect(POOL,plain,env,water));
        assertThrows(IllegalArgumentException.class,()->WishingWellLayout.inspect(POOL+"_no_effects",graph,env,water));
        assertThrows(IllegalArgumentException.class,()->WishingWellLayout.inspect(POOL,graph.subList(0,1),env,water));
        var child=parts.get(1); var shifted=new Placed(child.template(),child.origin().above(),child.rotation());
        assertThrows(IllegalArgumentException.class,()->WishingWellLayout.inspect(POOL,List.of(graph.get(0),shifted.piece()),env,water));
        var wet=new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.WATER,2);
        assertThrows(IllegalArgumentException.class,()->WishingWellLayout.inspect(POOL,graph,wet,water));
        assertThrows(IllegalArgumentException.class,()->WishingWellLayout.inspect(POOL,graph,env,water.subList(1,19)));
        var moved=layout.move(-111,15,8);
        for (var p:water) assertEquals(layout.pos(p.getX(),1,p.getZ()).offset(-111,15,8),moved.pos(p.getX(),1,p.getZ()));
        assertEquals(layout.pos(7,3,7).offset(-111,15,8),moved.pos(7,3,7));
    }

    @Test void pinnedResourcePoolsAndEntityPayloadArePreserved() throws Exception {
        assertEquals(HASH,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
        var templates=templates("wishing_well");
        assertEquals(7,templates.size());
        try (var zip=new ZipFile(JAR.toFile())) {
            String wg="data/dungeons_arise/worldgen/";
            var def=json(zip,wg+"structure/wishing_well.json");
            assertEquals("minecraft:jigsaw",def.get("type").getAsString());
            assertEquals(POOL,def.get("start_pool").getAsString());
            assertEquals(-7,def.getAsJsonObject("start_height").get("absolute").getAsInt());
            assertEquals("WORLD_SURFACE_WG",def.get("project_start_to_heightmap").getAsString());
            assertEquals("beard_thin",def.get("terrain_adaptation").getAsString());
            for (var name:List.of("main","main_no_effects","effects")) {
                var elements=json(zip,wg+"template_pool/prairie/wishing_well/wishing_well_"+name+".json").getAsJsonArray("elements");
                assertEquals(name.equals("effects") ? 5 : 1,elements.size());
                for (var raw:elements) {
                    var entry=raw.getAsJsonObject(); var element=entry.getAsJsonObject("element");
                    assertEquals("minecraft:empty",element.get("processors").getAsString());
                    assertEquals("rigid",element.get("projection").getAsString());
                    assertEquals("minecraft:single_pool_element",element.get("element_type").getAsString());
                    assertEquals(1,entry.get("weight").getAsInt());
                }
            }
        }
        assertEquals(Set.of(POOL),templates.get("0").pools());
        assertEquals(Set.of(POOL+"_no_effects"),templates.get("0_no_effects").pools());
        for (var t:templates.values()) {
            assertTrue(t.blocks().stream().noneMatch(b->b.data().contains("LootTable")));
            if (!t.id().startsWith("effect_")) {
                assertEquals(463,t.blocks().stream().filter(b->b.id().equals("minecraft:bedrock")).count());
                continue;
            }
            assertEquals(1,t.entities().size()); assertEquals(1,t.blocks().size());
            var nbt=t.entities().getCompound(0).getCompound("nbt");
            assertEquals(19999980,nbt.getInt("Duration")); assertEquals(0,nbt.getInt("WaitTime"));
            var motion=nbt.getList("Motion",Tag.TAG_DOUBLE);
            for (int i=0;i<3;i++) assertEquals(0,motion.getDouble(i));
        }
    }
}
