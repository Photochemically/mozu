package dev.expedition.bridge.content.wda;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
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

class PubAuditTest {
    private static final BlockPos ORIGIN=new BlockPos(-113,Y-19,221);
    private static final String POOL="dungeons_arise:greenwood_pub/greenwood_pub_";
    private static final List<String> BRANCHES=List.of("hallway_0","hallway_2","hallway_3","hallway_4","hallway_5","hallway_6",
            "stairs_0","stairs_1","room_0","room_1","room_2","room_3","room_4");
    private static final WdaStructurePlanner.Environment ENV=new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.GRASS);

    private static Block inlet(Template t,String name) {
        return t.blocks().stream().filter(b->b.data().getString("name").equals("dungeons_arise:"+name)).findFirst().orElseThrow();
    }
    private static Direction facing(Block b) { return Direction.byName(b.props().getString("orientation").split("_")[0]); }
    private static List<Placed> assemble(Map<String,Template> t,Rotation rotation,int variant) {
        var root=new Placed(t.get("0"),ORIGIN,rotation);
        var stairs=connect(root,root.template().joint("greenwood_pub_hallway",Direction.NORTH),t.get("stairs_2"),Direction.WEST);
        var branchTemplate=t.get(BRANCHES.get(variant%BRANCHES.size()));
        var branch=connect(stairs,stairs.template().joint("greenwood_pub_hallway",Direction.NORTH),
                branchTemplate,facing(inlet(branchTemplate,"greenwood_pub_hallway")));
        var parts=new ArrayList<>(List.of(root,stairs,branch));
        int furniture=variant==0?0:variant+1;
        for (var parent:List.copyOf(parts)) for (var socket:parent.template().blocks()) {
            String pool=socket.data().getString("pool");
            if (pool.equals(POOL+"furniture")) parts.add(connect(parent,socket,t.get("furniture_"+furniture),Direction.WEST));
            if (pool.equals(POOL+"spawners")) parts.add(connect(parent,socket,t.get("spawner_"+variant%2),Direction.DOWN));
        }
        // One native fallback closes the unused southern branch of the first cellar stair.
        var outlet=stairs.template().joint("greenwood_pub_hallway",Direction.SOUTH);
        var fallback=outlet.data().copy();
        // The native hallway pool falls back to terminators; its exact fallback is checked below.
        fallback.putString("pool",POOL+"terminators");
        parts.add(connect(stairs,new Block(outlet.pos(),outlet.id(),outlet.props(),fallback),t.get("terminator_0"),Direction.WEST));
        return parts;
    }
    private static List<PubLayout.Loot> loot(List<Placed> parts) {
        var out=new ArrayList<PubLayout.Loot>();
        for (var part:parts) for (var b:part.template().blocks()) if (b.data().contains("LootTable"))
            out.add(new PubLayout.Loot(part.at(b.pos()),b.id(),b.data().getString("LootTable")));
        return out;
    }
    private static List<Placed> finish(List<Placed> parts,boolean weathered) {
        if (!weathered) return parts;
        return parts.stream().map(p->{
            var t=p.template(); var blocks=t.blocks().stream().map(b->new Block(b.pos(),switch(b.id()) {
                case "minecraft:mud_bricks" -> "minecraft:packed_mud";
                case "minecraft:stone_bricks" -> "minecraft:mossy_stone_bricks";
                default -> b.id();
            },b.props(),b.data())).toList();
            return new Placed(new Template(t.id(),t.resource(),t.pools(),t.legacy(),t.size(),blocks,t.entities()),p.origin(),p.rotation());
        }).toList();
    }
    private static final class Model extends Terrain implements PubRoute.Probe {
        final PubLayout pub;
        final IslandPlan reserved;
        Model(List<Placed> parts,IslandPlan.Surface surface,boolean weathered) {
            super(finish(parts,weathered));
            pub=PubLayout.inspect(parts.stream().map(Placed::piece).toList(),new WdaStructurePlanner.Environment(Y,8,6,16,surface),PubAuditTest.loot(parts));
            reserved=new IslandPlan(island.building(),pub.groundY(),8,6,16,surface);
        }
        @Override public Type at(Cell p) {
            if (blocked.contains(p)) return Type.BLOCKED;
            if (!placed.containsKey(p)) return p.y()<pub.groundY()?Type.SUPPORT:Type.AIR;
            return super.at(p);
        }
        public boolean stair(Cell p,String kind,Direction direction,String shape) {
            var s=placed.get(p);
            return !blocked.contains(p) && s!=null && s.id().equals("minecraft:"+kind) && s.facing()==direction
                    && s.props().getString("shape").equals(shape) && s.props().getString("half").equals("bottom")
                    && s.props().getString("waterlogged").equals("false");
        }
        public boolean slab(Cell p) {
            var s=placed.get(p);
            return !blocked.contains(p) && s!=null && s.id().equals("minecraft:dark_oak_slab")
                    && s.props().getString("type").equals("bottom") && s.props().getString("waterlogged").equals("false");
        }
        public boolean loot(PubLayout.Loot l) {
            var p=new Cell(l.position().getX(),l.position().getY(),l.position().getZ()); var s=placed.get(p);
            return !blocked.contains(p) && s!=null && s.id().equals(l.kind()) && s.data().getString("LootTable").equals(l.table());
        }
        void survey(int exits) {
            var route=PubRoute.survey(pub,reserved,exits,this);
            for (int i=0;i<60000;i++) if (route.getAsBoolean()) return;
            fail("Pub entrance exceeded model budget");
        }
    }
    @Test void originalStairsAndHalfSlabsReachTheHallForAllRequestedExits() throws Exception {
        var t=templates("greenwood_pub"); var rows=new ArrayList<Map<String,Object>>(); var seen=new TreeSet<String>();
        for (var surface:List.of(IslandPlan.Surface.GRASS,IslandPlan.Surface.ROCK)) for (var rot:Rotation.values())
            for (int variant=0;variant<16;variant++) for (boolean weathered:List.of(false,true)) {
                var parts=assemble(t,rot,variant); var model=new Model(parts,surface,weathered);
                for (int exits=1;exits<=4;exits++) model.survey(exits);
                assertEquals(Y+1,model.pub.groundY()); assertTrue(model.reserved.completeBounds().minY()<ORIGIN.getY()-4);
                for (var part:parts) { seen.add(part.template().id());
                    for (var b:part.template().blocks()) if (b.data().contains("LootTable")||b.id().equals("minecraft:spawner")) {
                        var p=part.at(b.pos()); assertEquals(b.data(),model.placed.get(new Cell(p.getX(),p.getY(),p.getZ())).data());
                    }
                }
                var row=new LinkedHashMap<String,Object>(); row.put("surface",surface);row.put("rotation",rot);row.put("variant",variant);
                row.put("weathered",weathered);row.put("pieces",parts.size());row.put("lootContainers",model.pub.loot().size());
                row.put("exitCounts",List.of(1,2,3,4));rows.add(row);
            }
        assertEquals(256,rows.size());
        var expected=new TreeSet<>(t.keySet());expected.removeAll(Set.of("furniture_1","hallway_1","furniture_17"));
        assertEquals(expected,seen);
        var report=new LinkedHashMap<String,Object>();report.put("wdaSha256",HASH);report.put("runtimeAccepted",false);
        report.put("scope","256 source assemblies, all 34 reachable templates, native and fully weathered solid-material endpoints, 1..4 exits. Native random graph, collision, villagers, combat, deeper routes and persistence pending.");
        report.put("unusedTemplates",List.of("furniture_1","hallway_1"));report.put("incompatibleFurnitureInlet","furniture_17");
        report.put("cases",rows);var file=Path.of("build/reports/pub-entry-audit.json");Files.createDirectories(file.getParent());
        Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
    }
    @Test void missingStairsSlabsHeadroomLootOrExitRejectTheEntrance() throws Exception {
        var m=new Model(assemble(templates("greenwood_pub"),Rotation.CLOCKWISE_90,4),IslandPlan.Surface.GRASS,false);
        for (var p:List.of(m.pub.at(3,20,22),m.pub.at(5,23,22),m.pub.at(14,24,18),m.pub.at(16,27,17),
                m.pub.at(16,19,8),m.reserved.perimeterSites(4).get(3))) {
            m.blocked.add(p);assertThrows(IllegalStateException.class,()->m.survey(4));m.blocked.clear();
        }
        m.survey(4);
    }
    @Test void cellarConnectionProjectionGroundAndRelocationAreChecked() throws Exception {
        var parts=assemble(templates("greenwood_pub"),Rotation.CLOCKWISE_180,0);
        var graph=parts.stream().map(Placed::piece).toList();var pub=PubLayout.inspect(graph,ENV,loot(parts));
        var moved=pub.move(33,12,-57);assertEquals(pub.at(17,26,13).move(33,12,-57),moved.at(17,26,13));
        assertEquals(pub.loot().get(0).position().offset(33,12,-57),moved.loot().get(0).position());
        var missing=new ArrayList<>(graph);missing.remove(1);assertThrows(IllegalArgumentException.class,()->PubLayout.inspect(missing,ENV,loot(parts)));
        for (int i:List.of(0,1,2)) {
            var wrong=new ArrayList<>(graph);var p=wrong.get(i);
            wrong.set(i,new WdaTemplatePiece(p.id(),p.origin(),p.rotation(),p.bounds(),true));
            assertThrows(IllegalArgumentException.class,()->PubLayout.inspect(wrong,ENV,loot(parts)));
        }
        assertThrows(IllegalArgumentException.class,()->PubLayout.inspect(graph,new WdaStructurePlanner.Environment(Y+1,8,6,16,IslandPlan.Surface.GRASS),loot(parts)));
        assertThrows(IllegalArgumentException.class,()->PubLayout.inspect(graph,new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.WATER,2),loot(parts)));
        var m=new Model(parts,IslandPlan.Surface.GRASS,false);
        assertThrows(IllegalStateException.class,()->PubRoute.survey(m.pub,m.island,1,m));
    }
    @Test void pinnedNativePoolsAndProcessorRulesRemainUnchanged() throws Exception {
        assertEquals(HASH,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
        var t=templates("greenwood_pub"); assertEquals(37,t.size());
        assertTrue(t.get("furniture_1").pools().isEmpty());assertTrue(t.get("hallway_1").pools().isEmpty());
        assertFalse(t.get("furniture_17").pools().isEmpty());
        assertTrue(t.get("furniture_17").blocks().stream().noneMatch(b->b.data().getString("name").equals("dungeons_arise:greenwood_pub_furniture_0")));
        try (var zip=new ZipFile(JAR.toFile())) {
            var definition=json(zip,"data/dungeons_arise/worldgen/structure/greenwood_pub.json");
            assertEquals(POOL+"main",definition.get("start_pool").getAsString());assertEquals(-18,definition.getAsJsonObject("start_height").get("absolute").getAsInt());
            assertEquals("WORLD_SURFACE_WG",definition.get("project_start_to_heightmap").getAsString());assertFalse(definition.has("terrain_adaptation"));
            var processors=json(zip,"data/dungeons_arise/worldgen/processor_list/greenwood_pub_main.json").getAsJsonArray("processors");
            assertEquals(1,processors.size());assertEquals(JsonParser.parseString(PubPlanner.RULE),processors.get(0));
            for (var pool:List.of("main","furniture","hallways","stairs","terminators","spawners")) {
                var json=json(zip,"data/dungeons_arise/worldgen/template_pool/greenwood_pub/greenwood_pub_"+pool+".json");
                assertEquals(pool.equals("hallways")?POOL+"terminators":"minecraft:empty",json.get("fallback").getAsString());
                for (var e:json.getAsJsonArray("elements")) {var el=e.getAsJsonObject().getAsJsonObject("element");
                    assertEquals(PubPlanner.PROCESSORS,el.get("processors").getAsString());assertEquals("rigid",el.get("projection").getAsString());
                    assertEquals("minecraft:single_pool_element",el.get("element_type").getAsString());
                }
            }
        }
        assertTrue(t.values().stream().flatMap(v->v.blocks().stream()).noneMatch(b->b.id().equals("minecraft:red_stained_glass")));
    }
    @Test void nativeVillagersLootAndNestedSpawnerEntitiesArePreserved() throws Exception {
        var t=templates("greenwood_pub");assertEquals(19,t.get("0").entities().size());
        assertEquals(17,t.get("0").blocks().stream().filter(b->b.data().contains("LootTable")).count());
        var ids=new HashMap<String,Integer>();
        for (var raw:t.get("0").entities()) {var n=((net.minecraft.nbt.CompoundTag)raw).getCompound("nbt");ids.merge(n.getString("id"),1,Integer::sum);}
        assertEquals(Map.of("minecraft:villager",17,"minecraft:item_frame",2),ids);
        for (int i=0;i<2;i++) {
            var spawner=t.get("spawner_"+i).blocks().stream().filter(b->b.id().equals("minecraft:spawner")).findFirst().orElseThrow();
            var entity=spawner.data().getCompound("SpawnData").getCompound("entity");
            assertEquals(i==0?"minecraft:skeleton":"minecraft:zombie",entity.getString("id"));
            assertEquals(i==0?30:2,entity.getFloat("Health"));
            if (i==0) assertFalse(entity.getList("Passengers",Tag.TAG_COMPOUND).isEmpty());else assertTrue(entity.getBoolean("IsBaby"));
        }
    }
}
