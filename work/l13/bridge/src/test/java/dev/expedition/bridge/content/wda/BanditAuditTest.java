package dev.expedition.bridge.content.wda;

import com.google.gson.GsonBuilder;
import dev.expedition.bridge.compat.WdaAliasData;
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

class BanditAuditTest {
    private static final BlockPos ORIGIN=new BlockPos(-113,Y-1,221);
    private static final WdaStructurePlanner.Environment ENV=new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.GRASS);
    private static List<Block> joints(Template t){return t.blocks().stream().filter(b->b.id().equals("minecraft:jigsaw")).toList();}
    private static Direction front(Block b){return Direction.byName(b.props().getString("orientation").split("_")[0]);}
    private static Map<String,Template> effective() throws Exception {
        var t=templates("bandit_village");var zero=t.get("deco_0");
        try(var zip=new ZipFile(JAR.toFile())) {
            var aliases=WdaAliasData.build(path->{try{var e=zip.getEntry("data/dungeons_arise/"+path);return e==null?null:zip.getInputStream(e).readAllBytes();}
                catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}});
            assertArrayEquals(zip.getInputStream(zip.getEntry("data/dungeons_arise/structures/bandit_village/bandit_village_deco_0.nbt")).readAllBytes(),
                    aliases.get("structures/bandit_village/bandit_village_deco_3.nbt"));
        }
        t.put("deco_3",new Template("deco_3",BanditLayout.PREFIX+"deco_3",zero.pools(),false,zero.size(),zero.blocks(),zero.entities()));
        t.remove("deco_13");t.remove("street_1");return t;
    }
    private static List<Placed> assemble(Map<String,Template> t,Rotation r,String plotId,int roadId,int hoglin) {
        var root=new Placed(t.get("street_2"),ORIGIN,r);
        var rt=t.get("street_"+roadId);var inlet=roadId==4?Direction.SOUTH:Direction.NORTH;
        var road=connect(root,root.template().joint("streets",Direction.SOUTH),rt,inlet);
        var pt=t.get(plotId);
        var socket=joints(rt).stream().filter(b->b.data().getString("pool").equals(BanditLayout.POOL+"houses"))
                .max(Comparator.comparingInt(b->b.pos().distManhattan(joints(rt).stream().filter(j->front(j)==inlet&&j.data().getString("name").equals("dungeons_arise:streets")).findFirst().orElseThrow().pos()))).orElseThrow();
        var pin=joints(pt).stream().filter(b->b.data().getString("name").equals("dungeons_arise:streets_entrance")).findFirst().orElseThrow();
        var plot=connect(road,socket,pt,front(pin));
        var parts=new ArrayList<>(List.of(root,road,plot));
        var end=joints(rt).stream().filter(b->b.data().getString("target").equals("dungeons_arise:streets_crossing")&&front(b)!=inlet).findFirst().orElseThrow();
        parts.add(connect(road,end,t.get("street_3"),Direction.NORTH));
        for(var j:joints(pt))if(front(j)==Direction.UP) {
            var target=j.data().getString("target");var enemy=t.get(target.contains("archer")?"archer_0":target.contains("assassin")?"assassin_0":"hoglin_"+hoglin);
            parts.add(connect(plot,j,enemy,Direction.DOWN));
        }
        return parts;
    }
    private static List<Placed> dense(Map<String,Template> t,Rotation r,List<String> plots,int batch) {
        var root=new Placed(t.get("street_2"),ORIGIN,r);var parts=new ArrayList<>(List.of(root));int index=0;
        for(var exit:joints(root.template())) {
            var inner=connect(root,exit,t.get("street_0"),Direction.NORTH);parts.add(inner);
            var crossing=connect(inner,inner.template().joint("streets_crossing",Direction.SOUTH),t.get("street_3"),Direction.NORTH);parts.add(crossing);
            var road=connect(crossing,crossing.template().joint("streets",Direction.SOUTH),t.get("street_0"),Direction.NORTH);parts.add(road);
            for(var side:List.of(Direction.WEST,Direction.EAST)) {
                var socket=joints(road.template()).stream().filter(j->j.pos().getZ()==7&&front(j)==side).findFirst().orElseThrow();
                var pt=t.get(plots.get((batch*8+index++)%plots.size()));
                var pin=joints(pt).stream().filter(j->j.data().getString("name").equals("dungeons_arise:streets_entrance")).findFirst().orElseThrow();
                var plot=connect(road,socket,pt,front(pin));parts.add(plot);
                for(var j:joints(pt))if(front(j)==Direction.UP) {
                    var target=j.data().getString("target");
                    parts.add(connect(plot,j,t.get(target.contains("archer")?"archer_0":target.contains("assassin")?"assassin_0":"hoglin_"+(batch%2)),Direction.DOWN));
                }
            }
        }
        return parts;
    }
    private static List<BanditLayout.Feature> features(List<Placed> parts) {
        var list=new ArrayList<BanditLayout.Feature>();
        for(var p:parts)for(var b:p.template().blocks())if(Set.of("minecraft:chest","minecraft:barrel","minecraft:water","minecraft:spawner").contains(b.id()))
            list.add(new BanditLayout.Feature(BanditLayout.point(p.at(b.pos())),b.id(),b.data().getString("LootTable"),b.id().equals("minecraft:water")));
        return list;
    }
    private static class Model extends Terrain implements BanditRoute.Probe {
        final BanditLayout village;
        final IslandPlan reserved;
        int added;
        Model(List<Placed> parts,IslandPlan.Surface surface) {
            super(parts);
            village=BanditLayout.inspect(parts.stream().map(Placed::piece).toList(),new WdaStructurePlanner.Environment(Y,8,6,16,surface),features(parts));
            reserved=new IslandPlan(island.building().expand(8),Y,8,6,16,surface);
        }
        @Override public Type at(Cell p) {
            var s=placed.get(p);if(blocked.contains(p)||s==null||!s.id().equals("minecraft:red_sand"))return super.at(p);
            for(int d=1;d<=64;d++){
                var q=p.move(0,-d,0);var b=placed.get(q);
                if(blocked.contains(q))return Type.BLOCKED;
                if(b==null||!b.id().equals("minecraft:red_sand"))return super.at(q)==Type.SUPPORT?Type.SUPPORT:Type.BLOCKED;
            }
            return Type.BLOCKED;
        }
        public boolean stair(Cell p,String kind,Direction facing) {
            var s=placed.get(p);return !blocked.contains(p)&&s!=null&&s.id().equals("minecraft:"+kind)&&s.facing()==facing
                    &&s.props().getString("half").equals("bottom")&&s.props().getString("shape").equals("straight")&&s.props().getString("waterlogged").equals("false");
        }
        public boolean feature(BanditLayout.Feature f) {
            var s=placed.get(f.pos());return !blocked.contains(f.pos())&&s!=null&&s.id().equals(f.block())
                    &&(!f.source()||s.props().getString("level").equals("0"))&&s.data().getString("LootTable").equals(f.loot());
        }
        public boolean stepBlock(Cell p){var s=placed.get(p);return !blocked.contains(p)&&s!=null&&s.id().equals("minecraft:smooth_sandstone");}
        public void addStep(Cell p){
            assertEquals(Type.AIR,at(p));added++;placed.put(p,new State("minecraft:smooth_sandstone",new CompoundTag(),null,new CompoundTag()));
        }
        void survey(int exits) {
            var route=new BanditRoute(village,reserved,exits,this);
            for(int i=0;i<70000;i++)if(route.step())return;
            fail("Bandit route exceeded model budget");
        }
    }
    @Test void projectedRoadsEveryPlotAndEncountersRetainRoutesAcrossRotationsAndSurfaces() throws Exception {
        var t=effective();var plots=t.keySet().stream().filter(k->k.startsWith("house_")||k.startsWith("deco_")).sorted().toList();
        var rows=new ArrayList<Map<String,Object>>();var seen=new TreeSet<String>();
        for(var surface:List.of(IslandPlan.Surface.GRASS,IslandPlan.Surface.ROCK))for(var rotation:Rotation.values())for(int i=0;i<plots.size();i++){
            int road=new int[]{0,4,5}[i%3];var parts=assemble(t,rotation,plots.get(i),road,(i+rotation.ordinal())%2);
            var m=new Model(parts,surface);
            for(int exits=1;exits<=4;exits++)m.survey(exits);
            int expectedSteps=plots.get(i).equals("house_1")||plots.get(i).equals("house_4")?1:0;assertEquals(expectedSteps,m.added);
            for(var p:parts){seen.add(p.template().id());for(var b:p.template().blocks())if(b.data().contains("LootTable")||b.id().equals("minecraft:spawner"))
                assertEquals(b.data(),m.placed.get(BanditLayout.point(p.at(b.pos()))).data());}
            var row=new LinkedHashMap<String,Object>();row.put("surface",surface);row.put("rotation",rotation);row.put("plot",plots.get(i));
            row.put("road",road);row.put("pieces",parts.size());row.put("features",m.village.features().size());row.put("addedSteps",m.added);row.put("exitCounts",List.of(1,2,3,4));rows.add(row);
        }
        for(var surface:List.of(IslandPlan.Surface.GRASS,IslandPlan.Surface.ROCK))for(var rotation:Rotation.values())for(int batch=0;batch<3;batch++) {
            var parts=dense(t,rotation,plots,batch);var m=new Model(parts,surface);
            assertEquals(8,m.village.plots().size());
            for(int exits=1;exits<=4;exits++)m.survey(exits);
            for(var p:parts)for(var b:p.template().blocks())if(b.data().contains("LootTable")||b.id().equals("minecraft:spawner"))
                assertEquals(b.data(),m.placed.get(BanditLayout.point(p.at(b.pos()))).data());
            var row=new LinkedHashMap<String,Object>();row.put("surface",surface);row.put("rotation",rotation);row.put("denseBatch",batch);
            row.put("plots",8);row.put("pieces",parts.size());row.put("features",m.village.features().size());row.put("addedSteps",m.added);row.put("exitCounts",List.of(1,2,3,4));rows.add(row);
        }
        assertEquals(208,rows.size());assertEquals(t.keySet(),seen);
        var report=new LinkedHashMap<String,Object>();report.put("wdaSha256",HASH);report.put("runtimeAccepted",false);
        report.put("scope","208 source assemblies (184 single-plot and 24 eight-plot), 31 reachable original templates plus approved deco_3 substitute; all ten house entrances, thirteen decor approaches, terrain-matching roads and all native encounter types. Four rotations, grass/rock, 1-4 exits. Two outside steps preserve every original block. Random graph, collision, falling sand, fluid physics, combat and persistence pending.");
        report.put("cases",rows);var path=Path.of("build/reports/bandit-entry-audit.json");Files.createDirectories(path.getParent());
        Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
    }
    @Test void nativeCatalogMatchesEveryEffectiveTemplateAndOriginalConnectors() throws Exception {
        var t=effective();assertEquals(32,t.size());assertEquals(t.keySet(),BanditLayout.SHAPES.keySet());
        for(var entry:t.entrySet()){
            var actual=BanditLayout.SHAPES.get(entry.getKey());assertEquals(entry.getValue().size(),actual.size());
            var expected=joints(entry.getValue()).stream().map(b->{var o=b.props().getString("orientation").split("_");var n=b.data();
                return new BanditLayout.Joint(b.pos(),Direction.byName(o[0]),Direction.byName(o[1]),n.getString("name"),n.getString("target"),n.getString("pool"),n.getString("joint").equals("aligned"));}).toList();
            assertEquals(expected,actual.joints());
        }
    }
    @Test void blockedHouseStepsNativeFeaturesOrAnyExitRejectAndDoNotWrite() throws Exception {
        var t=effective();
        for(String house:List.of("house_1","house_4","house_6","deco_0","deco_6")) {
            var parts=assemble(t,Rotation.CLOCKWISE_90,house,0,0);var clean=new Model(parts,IslandPlan.Surface.GRASS);
            var targets=new ArrayList<Cell>();targets.add(clean.reserved.perimeterSites(4).get(3));
            var p=clean.village.plots().get(0);targets.add(p.approach());
            if(p.stepBlock()!=null){targets.add(p.stepBlock());targets.add(p.stepBlock().move(0,-1,0));targets.add(p.stepBlock().move(0,3,0));}
            if(house.equals("house_6"))targets.add(p.at(5,2,4));
            for(var f:clean.village.features())targets.add(f.pos());
            for(var blocked:targets){var m=new Model(parts,IslandPlan.Surface.GRASS);m.blocked.add(blocked);
                assertThrows(IllegalStateException.class,()->m.survey(4),house+" "+blocked);assertEquals(0,m.added);}
        }
    }
    @Test void disconnectedWrongGradeProjectionAndDuplicateGraphsAreRejected() throws Exception {
        var parts=assemble(effective(),Rotation.CLOCKWISE_180,"house_3",4,0);var graph=parts.stream().map(Placed::piece).toList();
        var layout=BanditLayout.inspect(graph,ENV,features(parts));var p=graph.get(2);
        for(var wrong:List.of(new WdaTemplatePiece(p.id(),p.origin(),p.rotation(),p.bounds(),true),
                new WdaTemplatePiece(p.id(),p.origin().above(),p.rotation(),p.bounds().move(0,1,0),false),
                new WdaTemplatePiece(p.id(),p.origin().offset(20,0,0),p.rotation(),p.bounds().move(20,0,0),false))) {
            var copy=new ArrayList<>(graph);copy.set(2,wrong);assertThrows(IllegalArgumentException.class,()->BanditLayout.inspect(copy,ENV,features(parts)));
        }
        var duplicate=new ArrayList<>(graph);duplicate.add(p);assertThrows(IllegalArgumentException.class,()->BanditLayout.inspect(duplicate,ENV,features(parts)));
        var detached=new ArrayList<>(graph);detached.remove(2);assertThrows(IllegalArgumentException.class,()->BanditLayout.inspect(detached,ENV,features(parts)));
        assertThrows(IllegalArgumentException.class,()->BanditLayout.inspect(graph,new WdaStructurePlanner.Environment(Y+1,8,6,16,IslandPlan.Surface.GRASS),features(parts)));
        assertThrows(IllegalArgumentException.class,()->BanditLayout.inspect(graph,new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.WATER,2),features(parts)));
        var moved=layout.move(17,24,-83);assertEquals(layout.center().move(17,24,-83),moved.center());
        assertEquals(layout.plots().get(0).approach().move(17,24,-83),moved.plots().get(0).approach());assertEquals(Y+24,moved.groundY());
        assertEquals(layout.features().get(0).pos().move(17,24,-83),moved.features().get(0).pos());
    }
    @Test void nativeEntityCoordinatesAndTypesMatchExactlyBeforeRelease() throws Exception {
        var t=effective();
        for(var rotation:Rotation.values())for(String house:List.of("house_3","house_5")) {
            var parts=assemble(t,rotation,house,5,0);var v=BanditLayout.inspect(parts.stream().map(Placed::piece).toList(),ENV,features(parts));
            var expected=new ArrayList<BanditLayout.Spawn>();
            for(var p:parts)for(var raw:p.template().entities()) {
                var e=(CompoundTag)raw;var xyz=e.getList("pos",Tag.TAG_DOUBLE);var local=new Vec3(xyz.getDouble(0),xyz.getDouble(1),xyz.getDouble(2));
                var pos=BlockPos.containing(StructureTemplate.transform(local,Mirror.NONE,p.rotation(),BlockPos.ZERO).add(p.origin().getX(),p.origin().getY(),p.origin().getZ()));
                expected.add(new BanditLayout.Spawn(pos,e.getCompound("nbt").getString("id")));
            }
            assertTrue(v.matchesEntities(expected));assertFalse(v.matchesEntities(expected.subList(1,expected.size())));
            var wrong=new ArrayList<>(expected);wrong.set(0,new BanditLayout.Spawn(expected.get(0).position(),"minecraft:zombie"));assertFalse(v.matchesEntities(wrong));
            wrong.set(0,new BanditLayout.Spawn(expected.get(0).position().above(),expected.get(0).type()));assertFalse(v.matchesEntities(wrong));
            wrong=new ArrayList<>(expected);wrong.add(expected.get(0));assertFalse(v.matchesEntities(wrong));
            assertTrue(v.move(5,7,-9).matchesEntities(expected.stream().map(s->new BanditLayout.Spawn(s.position().offset(5,7,-9),s.type())).toList()));
        }
    }
    @Test void pinnedPoolsRetainWeightsSubstitutionAndOriginalMountedEnemies() throws Exception {
        assertEquals(HASH,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
        var raw=templates("bandit_village");assertEquals(33,raw.size());assertTrue(raw.get("street_1").pools().isEmpty());assertTrue(raw.get("deco_13").pools().isEmpty());
        try(var zip=new ZipFile(JAR.toFile())) {
            var d=json(zip,"data/dungeons_arise/worldgen/structure/bandit_village.json");
            assertEquals(BanditLayout.POOL+"start",d.get("start_pool").getAsString());assertEquals(7,d.get("size").getAsInt());
            assertEquals(114,d.get("max_distance_from_center").getAsInt());assertEquals("beard_thin",d.get("terrain_adaptation").getAsString());
            assertEquals("WORLD_SURFACE_WG",d.get("project_start_to_heightmap").getAsString());assertEquals(0,d.getAsJsonObject("start_height").get("absolute").getAsInt());
            for(String name:List.of("start","streets","houses","spawners")) {
                var pool=json(zip,"data/dungeons_arise/worldgen/template_pool/bandit/bandit_village/bandit_village_"+name+".json");
                assertEquals("minecraft:empty",pool.get("fallback").getAsString());
                var elements=pool.getAsJsonArray("elements");assertEquals(name.equals("houses")?23:name.equals("start")?1:4,elements.size());
                int weight=0;
                for(var e:elements){var item=e.getAsJsonObject();weight+=item.get("weight").getAsInt();var el=item.getAsJsonObject("element");
                    assertEquals("minecraft:single_pool_element",el.get("element_type").getAsString());assertEquals("minecraft:empty",el.get("processors").getAsString());
                    assertEquals(name.equals("start")||name.equals("streets")?"terrain_matching":"rigid",el.get("projection").getAsString());}
                assertEquals(name.equals("streets")?7:name.equals("houses")?23:name.equals("start")?1:4,weight);
            }
        }
        for(int i=0;i<2;i++){
            var spawner=raw.get("hoglin_"+i).blocks().stream().filter(b->b.id().equals("minecraft:spawner")).findFirst().orElseThrow();
            var mob=spawner.data().getCompound("SpawnData").getCompound("entity");assertEquals("minecraft:hoglin",mob.getString("id"));assertTrue(mob.getBoolean("IsImmuneToZombification"));
            assertEquals(i==0?"minecraft:wither_skeleton":"minecraft:husk",mob.getList("Passengers",Tag.TAG_COMPOUND).getCompound(0).getString("id"));
        }
        var rocket=raw.get("house_6").blocks().stream().filter(b->b.id().equals("minecraft:spawner")).findFirst().orElseThrow();
        assertEquals("minecraft:firework_rocket",rocket.data().getCompound("SpawnData").getCompound("entity").getString("id"));
    }
}
