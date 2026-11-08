package dev.expedition.bridge.content.wda;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import static dev.expedition.bridge.content.wda.WdaTemplateFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class VillageAuditTest {
    private static final BlockPos ORIGIN=new BlockPos(-113,Y-1,221);
    private static final String POOL="dungeons_arise:fungi/mushroom_village/mushroom_village_";
    private static final WdaStructurePlanner.Environment ENV=new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.GRASS);
    private static List<Block> sockets(Template t) {return t.blocks().stream().filter(b->b.data().getString("target").equals("minecraft:side_1")).toList();}
    private static Direction inlet(Template t) {
        return t.blocks().stream().filter(b->b.data().getString("name").equals("minecraft:side_1"))
                .map(b->Direction.byName(b.props().getString("orientation").split("_")[0])).findFirst().orElseThrow();
    }
    private static boolean overlaps(Placed a,Placed b) {
        var p=a.bounds();var q=b.bounds();
        return p.minX()<=q.maxX() && p.maxX()>=q.minX() && p.minY()<=q.maxY() && p.maxY()>=q.minY() && p.minZ()<=q.maxZ() && p.maxZ()>=q.minZ();
    }
    private static List<Placed> assemble(Map<String,Template> templates,Rotation r,int variant,int density) {
        var root=new Placed(templates.get("house_big_0"),ORIGIN,r);var parts=new ArrayList<>(List.of(root));
        var sockets=new ArrayList<>(sockets(root.template()));Collections.rotate(sockets,variant);
        for (int i=0;i<sockets.size();i++) {
            if (density==0 && i>0) break;
            if (density==1 && i%3!=0) continue;
            var t=templates.get("house_small_"+(variant+i)%8);var p=connect(root,sockets.get(i),t,inlet(t));
            if (parts.stream().noneMatch(other->overlaps(p,other))) parts.add(p);
        }
        return parts;
    }
    private static List<Placed> processed(List<Placed> parts) {
        return parts.stream().map(p->{
            var t=p.template();var blocks=t.blocks().stream().map(b->{
                if (!b.id().equals("minecraft:light_gray_wool")) return b;
                var props=new CompoundTag();for(var d:List.of("down","east","north","south","up","west")) props.putString(d,"false");
                return new Block(b.pos(),"minecraft:mushroom_stem",props,b.data());
            }).toList();
            return new Placed(new Template(t.id(),t.resource(),t.pools(),t.legacy(),t.size(),blocks,t.entities()),p.origin(),p.rotation());
        }).toList();
    }
    private static final class Model extends Terrain implements VillageRoute.Probe {
        final VillageLayout village;
        final IslandPlan reserved;
        Model(List<Placed> parts,IslandPlan.Surface surface) {
            super(processed(parts));
            village=VillageLayout.inspect(parts.stream().map(Placed::piece).toList(),new WdaStructurePlanner.Environment(Y,8,6,16,surface));
            reserved=new IslandPlan(island.building().expand(12),Y,8,6,16,surface);
        }
        @Override public Type at(Cell p) {
            var s=placed.get(p);
            if (!blocked.contains(p) && s!=null && Set.of("minecraft:mycelium","minecraft:moss_block").contains(s.id())) return Type.SUPPORT;
            return super.at(p);
        }
        public boolean door(Cell p,Direction facing) {
            var a=placed.get(p);var b=placed.get(p.move(0,1,0));
            if(blocked.contains(p)||blocked.contains(p.move(0,1,0))||a==null||b==null)return false;
            var props=a.props().copy();props.putString("half","upper");
            return a.id().equals("minecraft:birch_door")&&b.id().equals(a.id())&&a.facing()==facing&&b.facing()==facing
                    &&a.props().getString("half").equals("lower")&&props.equals(b.props());
        }
        public boolean halfSlab(Cell p) {
            var s=placed.get(p);return !blocked.contains(p)&&s!=null&&s.id().equals("minecraft:birch_slab")
                    &&s.props().getString("type").equals("bottom")&&s.props().getString("waterlogged").equals("false");
        }
        public boolean loot(VillageLayout.Loot l) {
            var s=placed.get(l.pos());return !blocked.contains(l.pos())&&s!=null&&s.id().equals("minecraft:"+l.kind())
                    &&s.data().getString("LootTable").equals("dungeons_arise:chests/mushroom_village/mushroom_village_"+l.tier());
        }
        public boolean water(Cell p) {var s=placed.get(p);return !blocked.contains(p)&&s!=null&&s.id().equals("minecraft:water")&&s.props().getString("level").equals("0");}
        public boolean spawner(Cell p) {var s=placed.get(p);return !blocked.contains(p)&&s!=null&&s.id().equals("minecraft:spawner")&&s.data().contains("SpawnPotentials");}
        void survey(int exits) {
            var route=new VillageRoute(village,reserved,exits,this);
            for(int i=0;i<70000;i++)if(route.step())return;
            fail("Village route exceeded model budget");
        }
    }
    @Test void mainHallEveryGeneratedHouseAndAllExitsKeepTheirOriginalRoutes() throws Exception {
        var t=templates("mushroom_village");var rows=new ArrayList<Map<String,Object>>();var seen=new TreeSet<String>();
        for(var surface:List.of(IslandPlan.Surface.GRASS,IslandPlan.Surface.ROCK))for(var r:Rotation.values())
            for(int variant=0;variant<8;variant++)for(int density=0;density<3;density++) {
                var parts=assemble(t,r,variant,density);var m=new Model(parts,surface);
                for(int exits=1;exits<=4;exits++)m.survey(exits);
                int loot=0,water=0,spawners=0,turtles=0;
                for(var p:parts) {seen.add(p.template().id());turtles+=p.template().entities().size();
                    for(var b:p.template().blocks()) {
                        var q=p.at(b.pos());var cell=new Cell(q.getX(),q.getY(),q.getZ());var actual=m.placed.get(cell);
                        if(b.data().contains("LootTable")||b.id().equals("minecraft:spawner"))assertEquals(b.data(),actual.data());
                        if(b.data().contains("LootTable"))loot++;
                        if(b.id().equals("minecraft:spawner"))spawners++;
                        if(b.id().equals("minecraft:water")){water++;assertTrue(m.water(cell));}
                        if(b.id().equals("minecraft:light_gray_wool"))assertEquals("minecraft:mushroom_stem",actual.id());
                    }
                }
                assertEquals(m.village.loot().size(),loot);assertEquals(m.village.water().size(),water);assertEquals(3,spawners);assertEquals(m.village.turtles().size(),turtles);
                var row=new LinkedHashMap<String,Object>();row.put("surface",surface);row.put("rotation",r);row.put("variant",variant);row.put("density",density);
                row.put("houses",parts.size()-1);row.put("lootContainers",loot);row.put("waterSources",water);row.put("spawners",spawners);row.put("turtles",turtles);
                row.put("exitCounts",List.of(1,2,3,4));rows.add(row);
            }
        assertEquals(192,rows.size());assertEquals(t.keySet(),seen);
        var report=new LinkedHashMap<String,Object>();report.put("wdaSha256",HASH);report.put("runtimeAccepted",false);report.put("socketCombinations",384);
        report.put("scope","192 non-overlapping source assemblies across all nine templates, four rotations, grass/rock and three attachment densities. All requested exits, each attached entrance, native half-slab slope, loot, water and spawner payloads checked. Native random graph, collision, fluid physics, combat and persistence pending.");
        report.put("cases",rows);var path=Path.of("build/reports/village-entry-audit.json");Files.createDirectories(path.getParent());
        Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
    }
    @Test void everyNativeSocketAcceptsAllEightHouseInletsAndRotations() throws Exception {
        var t=templates("mushroom_village");int count=0;
        for(var r:Rotation.values()) {
            var root=new Placed(t.get("house_big_0"),ORIGIN,r);assertEquals(12,sockets(root.template()).size());
            for(var socket:sockets(root.template()))for(int i=0;i<8;i++) {
                var house=t.get("house_small_"+i);var p=connect(root,socket,house,inlet(house));
                var v=VillageLayout.inspect(List.of(root.piece(),p.piece()),ENV);assertEquals(1,v.houses().size());count++;
            }
        }
        assertEquals(384,count);
        var rootOnly=new Model(List.of(new Placed(t.get("house_big_0"),ORIGIN,Rotation.NONE)),IslandPlan.Surface.GRASS);
        rootOnly.survey(4);assertTrue(rootOnly.village.matchesTurtles(List.of()));
    }
    @Test void blockedSlopeDoorExitLootWaterAndEncounterRejectRelease() throws Exception {
        var m=new Model(assemble(templates("mushroom_village"),Rotation.CLOCKWISE_90,0,0),IslandPlan.Surface.GRASS);
        var h=m.village.houses().get(0);
        for(var p:List.of(m.village.at(32,1,36),m.village.at(29,5,34),h.at(5,1,8),h.at(5,2,8),
                m.reserved.perimeterSites(4).get(3),m.village.loot().get(0).pos(),m.village.water().get(0),m.village.spawners().get(0))) {
            m.blocked.add(p);assertThrows(IllegalStateException.class,()->m.survey(4));m.blocked.clear();
        }
        m.survey(4);
    }
    @Test void geometryRejectsDetachedOverlappingOrProjectedAttachmentsAndRelocatesMarkers() throws Exception {
        var parts=assemble(templates("mushroom_village"),Rotation.CLOCKWISE_180,4,1);var graph=parts.stream().map(Placed::piece).toList();
        var v=VillageLayout.inspect(graph,ENV);var moved=v.move(17,24,-83);
        assertEquals(v.water().get(0).move(17,24,-83),moved.water().get(0));assertEquals(v.houses().get(0).approach().move(17,24,-83),moved.houses().get(0).approach());
        var p=graph.get(1);
        for(var wrongPiece:List.of(new WdaTemplatePiece(p.id(),p.origin(),p.rotation(),p.bounds(),true),
                new WdaTemplatePiece(p.id(),p.origin().above(),p.rotation(),p.bounds().move(0,1,0),false),
                new WdaTemplatePiece(p.id(),p.origin().offset(2,0,0),p.rotation(),p.bounds().move(2,0,0),false))) {
            var wrong=new ArrayList<>(graph);wrong.set(1,wrongPiece);assertThrows(IllegalArgumentException.class,()->VillageLayout.inspect(wrong,ENV));
        }
        var duplicate=new ArrayList<>(graph);duplicate.add(p);assertThrows(IllegalArgumentException.class,()->VillageLayout.inspect(duplicate,ENV));
        assertThrows(IllegalArgumentException.class,()->VillageLayout.inspect(graph,new WdaStructurePlanner.Environment(Y+1,8,6,16,IslandPlan.Surface.GRASS)));
        assertThrows(IllegalArgumentException.class,()->VillageLayout.inspect(graph,new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.WATER,2)));
    }
    @Test void originalTurtleEntityCoordinatesMatchAfterEveryRotationAndMove() throws Exception {
        var t=templates("mushroom_village");var pond=t.get("house_small_7");assertEquals(1,pond.entities().size());
        var entity=pond.entities().getCompound(0);assertEquals("minecraft:turtle",entity.getCompound("nbt").getString("id"));
        var xyz=entity.getList("pos",Tag.TAG_DOUBLE);var local=new Vec3(xyz.getDouble(0),xyz.getDouble(1),xyz.getDouble(2));
        for(var r:Rotation.values()) {
            var root=new Placed(t.get("house_big_0"),ORIGIN,r);var parts=new ArrayList<>(List.of(root));
            for(int i:List.of(0,6))parts.add(connect(root,sockets(root.template()).get(i),pond,inlet(pond)));
            var v=VillageLayout.inspect(parts.stream().map(Placed::piece).toList(),ENV);
            var actual=parts.subList(1,parts.size()).stream().map(p->BlockPos.containing(StructureTemplate.transform(local,Mirror.NONE,p.rotation(),BlockPos.ZERO)
                    .add(p.origin().getX(),p.origin().getY(),p.origin().getZ()))).toList();
            assertTrue(v.matchesTurtles(actual));assertFalse(v.matchesTurtles(actual.subList(0,1)));
            assertFalse(v.matchesTurtles(List.of(actual.get(0),actual.get(0))));
            assertFalse(v.matchesTurtles(List.of(actual.get(0),actual.get(1).above())));
            assertTrue(v.move(5,7,-9).matchesTurtles(actual.stream().map(p->p.offset(5,7,-9)).toList()));
        }
    }
    @Test void pinnedPoolsLocationPredicatesAndOriginalSpawnVariantsAreRetained() throws Exception {
        assertEquals(HASH,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
        var t=templates("mushroom_village");assertEquals(9,t.size());
        try(var zip=new ZipFile(JAR.toFile())) {
            var d=json(zip,"data/dungeons_arise/worldgen/structure/mushroom_village.json");
            assertEquals(POOL+"start",d.get("start_pool").getAsString());assertEquals("beard_thin",d.get("terrain_adaptation").getAsString());
            assertEquals(0,d.getAsJsonObject("start_height").get("absolute").getAsInt());assertEquals("WORLD_SURFACE_WG",d.get("project_start_to_heightmap").getAsString());
            var processors=json(zip,"data/dungeons_arise/worldgen/processor_list/mushroom_village_houses.json").getAsJsonArray("processors");
            assertEquals(JsonParser.parseString(VillagePlanner.RULES),processors);assertEquals(2,processors.size());
            for(var pool:List.of("start","houses")) {
                var json=json(zip,"data/dungeons_arise/worldgen/template_pool/fungi/mushroom_village/mushroom_village_"+pool+".json");
                assertEquals("minecraft:empty",json.get("fallback").getAsString());
                assertEquals(pool.equals("start")?1:8,json.getAsJsonArray("elements").size());
                for(var raw:json.getAsJsonArray("elements")) {var e=raw.getAsJsonObject();assertEquals(1,e.get("weight").getAsInt());var el=e.getAsJsonObject("element");
                    assertEquals(VillagePlanner.PROCESSORS,el.get("processors").getAsString());assertEquals("rigid",el.get("projection").getAsString());
                    assertEquals("minecraft:single_pool_element",el.get("element_type").getAsString());
                }
            }
        }
        int spawners=0;
        for(var b:t.get("house_big_0").blocks())if(b.id().equals("minecraft:spawner")) {
            spawners++;var potentials=b.data().getList("SpawnPotentials",Tag.TAG_COMPOUND);assertEquals(3,potentials.size());
            var ids=new HashSet<String>();
            for(var raw:potentials) {var p=(CompoundTag)raw;assertEquals(2,p.getInt("weight"));var entity=p.getCompound("data").getCompound("entity");ids.add(entity.getString("id"));
                if(entity.getString("id").equals("minecraft:cow"))assertEquals("minecraft:piglin_brute",entity.getList("Passengers",Tag.TAG_COMPOUND).getCompound(0).getString("id"));
                else assertTrue(entity.getBoolean("IsImmuneToZombification"));
            }
            assertEquals(Set.of("minecraft:cow","minecraft:piglin","minecraft:piglin_brute"),ids);
        }
        assertEquals(3,spawners);
    }
}
