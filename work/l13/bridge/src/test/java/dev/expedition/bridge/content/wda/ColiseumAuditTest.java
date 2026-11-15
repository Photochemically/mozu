package dev.expedition.bridge.content.wda;

import com.google.gson.GsonBuilder;
import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
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

class ColiseumAuditTest {
    private static final BlockPos ORIGIN=new BlockPos(-113,Y-4,221);
    private static final WdaStructurePlanner.Environment ENV=new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.GRASS);
    private static List<Placed> assemble(Map<String,Template> templates,Rotation rotation,int root,Rotation enemyRotation){
        var zero=new Placed(templates.get("part_0"),ORIGIN,rotation);
        var one=connect(zero,zero.template().joint("coliseum_side_2",Direction.EAST),templates.get("part_1"),Direction.WEST);
        var three=connect(zero,zero.template().joint("coliseum_side_1",Direction.SOUTH),templates.get("part_3"),Direction.NORTH);
        var two=root%2==0?connect(one,one.template().joint("coliseum_side_5",Direction.SOUTH),templates.get("part_2"),Direction.NORTH)
                :connect(three,three.template().joint("coliseum_side_6",Direction.EAST),templates.get("part_2"),Direction.WEST);
        var parts=new ArrayList<>(List.of(zero,one,two,three));
        for(var p:List.copyOf(parts))for(var b:p.template().blocks())if(b.data().getString("pool").endsWith("coliseum_spawners")){
            var t=templates.get(b.data().getString("target").endsWith("armored_iron")?"enemy_0":"spawner_0");
            parts.add(connect(p,b,t,Direction.DOWN,enemyRotation));
        }
        Collections.swap(parts,0,root);return parts;
    }
    private static List<ColiseumLayout.Device> devices(List<Placed> parts){
        var result=new ArrayList<ColiseumLayout.Device>();for(var p:parts)for(var b:p.template().blocks())if(b.id().equals("minecraft:spawner")){
            var n=b.data();var pos=p.at(b.pos());result.add(new ColiseumLayout.Device(new Cell(pos.getX(),pos.getY(),pos.getZ()),n.getCompound("SpawnData").copy(),n.getList("SpawnPotentials",Tag.TAG_COMPOUND).copy()));
        }return result;
    }
    private static ColiseumLayout inspect(List<Placed> parts){return ColiseumLayout.inspect(parts.stream().map(Placed::piece).toList(),ENV,devices(parts));}
    private static List<ColiseumLayout.Spawn> entities(List<Placed> parts){
        var result=new ArrayList<ColiseumLayout.Spawn>();for(var p:parts)for(var raw:p.template().entities()){
            var n=(CompoundTag)raw;var pos=n.getList("pos",Tag.TAG_DOUBLE);
            var point=StructureTemplate.transform(new Vec3(pos.getDouble(0),pos.getDouble(1),pos.getDouble(2)),Mirror.NONE,p.rotation(),BlockPos.ZERO)
                    .add(p.origin().getX(),p.origin().getY(),p.origin().getZ());
            result.add(new ColiseumLayout.Spawn(BlockPos.containing(point),n.getCompound("nbt").getString("id")));
        }return result;
    }
    private static class Model extends Terrain implements ColiseumRoute.Probe {
        final ColiseumLayout layout;
        final IslandPlan reserved;
        Model(List<Placed> parts,IslandPlan.Surface surface){
            super(parts);layout=inspect(parts);
            // StructureStart expands the complete raw box by 12 for the original beard_box setting.
            reserved=new IslandPlan(island.building().expand(12),layout.groundY(),8,6,16,surface);
        }
        @Override public Type at(Cell p){if(blocked.contains(p))return Type.BLOCKED;if(!placed.containsKey(p))return p.y()<layout.groundY()?Type.SUPPORT:Type.AIR;return super.at(p);}
        public boolean device(ColiseumLayout.Device d){var s=placed.get(d.pos());return !blocked.contains(d.pos())&&s!=null&&s.id().equals("minecraft:spawner")
                &&s.data().getCompound("SpawnData").equals(d.spawnData())&&s.data().getList("SpawnPotentials",Tag.TAG_COMPOUND).equals(d.potentials());}
        void survey(int exits){var route=new ColiseumRoute(layout,reserved,exits,this);for(int i=0;i<30000;i++)if(route.step())return;fail("Coliseum route budget exceeded");}
    }
    @Test void originalQuadrantsAndEncountersReachCoveredArcadesAcrossRootsAndRotations() throws Exception {
        var templates=templates("coliseum");var rows=new ArrayList<Map<String,Object>>();var seen=new TreeSet<String>();
        for(var surface:List.of(IslandPlan.Surface.GRASS,IslandPlan.Surface.ROCK))for(var rotation:Rotation.values())for(int root=0;root<4;root++){
            var parts=assemble(templates,rotation,root,Rotation.values()[(root+rotation.ordinal())%4]);var m=new Model(parts,surface);var before=Map.copyOf(m.placed);
            for(int exits=1;exits<=4;exits++)m.survey(exits);
            assertEquals(before,m.placed);assertEquals(16,parts.size());assertEquals(Y-2,m.reserved.firstFreeY());
            assertEquals(Type.AIR,m.at(m.layout.at(31,2,20)),"Sparse arena air must not be buried");
            assertEquals(ORIGIN.getY()+1,m.reserved.column(m.layout.at(31,1,20).x(),m.layout.at(31,1,20).z()).top());
            assertEquals(88,m.reserved.building().width());assertEquals(88,m.reserved.building().depth());
            assertTrue(m.layout.matchesEntities(entities(parts)));assertEquals(11,m.layout.inhabitants().size());
            for(var p:parts)seen.add(p.template().id());
            rows.add(Map.of("rotation",rotation,"root",root,"surface",surface,"pieces",parts.size(),"skeletons",11,"spawners",1,"islandFirstFreeY",m.reserved.firstFreeY(),"exitCounts",List.of(1,2,3,4)));
        }
        assertEquals(templates.keySet(),seen);var report=new LinkedHashMap<String,Object>();report.put("jarSha256",HASH);report.put("structure",ColiseumLayout.TARGET);report.put("cases",rows);report.put("sourceTemplates",seen);
        report.put("nativeGeneratorExecuted",false);report.put("runtimeTested",false);report.put("scope","32 complete four-quadrant original-template assemblies, 11 skeletons and one phantom spawner. Four roots, four rotations, two surfaces and 1..4 exits. Route reaches original covered outer arcade; full stands/arena traversal remains a runtime check.");
        Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/coliseum-entry-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    @Test void anyRemainingOriginalArchCanBeUsedButAllBlockedOrExitBlockedReject() throws Exception {
        var parts=assemble(templates("coliseum"),Rotation.NONE,0,Rotation.NONE);
        for(int usable=0;usable<4;usable++){
            var m=new Model(parts,IslandPlan.Surface.GRASS);for(int side=0;side<4;side++)if(side!=usable)m.blocked.add(m.layout.arch(side,2));m.survey(4);
        }
        var all=new Model(parts,IslandPlan.Surface.ROCK);for(int side=0;side<4;side++)all.blocked.add(all.layout.arch(side,2));assertThrows(IllegalStateException.class,()->all.survey(1));
        var exit=new Model(parts,IslandPlan.Surface.ROCK);exit.blocked.add(exit.reserved.perimeterSites(4).get(3));assertThrows(IllegalStateException.class,()->exit.survey(4));
    }
    @Test void missingOrMisorientedPiecesFailBeforeAllocation() throws Exception {
        var parts=assemble(templates("coliseum"),Rotation.CLOCKWISE_90,3,Rotation.CLOCKWISE_180);
        for(int i=0;i<parts.size();i++){var bad=new ArrayList<>(parts);bad.remove(i);assertThrows(IllegalArgumentException.class,()->inspect(bad));}
        for(int i:List.of(0,4,parts.size()-1)){var bad=new ArrayList<>(parts);var p=bad.get(i);bad.set(i,new Placed(p.template(),p.origin().offset(1,1,0),p.rotation()));assertThrows(IllegalArgumentException.class,()->inspect(bad));}
        var duplicate=new ArrayList<>(parts);duplicate.add(parts.get(4));assertThrows(IllegalArgumentException.class,()->inspect(duplicate));
        for(int i=0;i<parts.size();i++)if(!parts.get(i).template().id().equals("enemy_0")){
            var bad=new ArrayList<>(parts);var p=bad.get(i);bad.set(i,new Placed(p.template(),p.origin(),p.rotation().getRotated(Rotation.CLOCKWISE_90)));assertThrows(IllegalArgumentException.class,()->inspect(bad));
        }
    }
    @Test void originalSpawnerAndArcadeHeightCannotBeReplacedOrBuried() throws Exception {
        var parts=assemble(templates("coliseum"),Rotation.COUNTERCLOCKWISE_90,1,Rotation.NONE);var m=new Model(parts,IslandPlan.Surface.ROCK);
        var d=m.layout.spawner();var state=m.placed.get(d.pos());var changed=state.data().copy();changed.getCompound("SpawnData").getCompound("entity").putString("id","minecraft:zombie");
        m.placed.put(d.pos(),new State(state.id(),state.props(),state.facing(),changed));assertThrows(IllegalStateException.class,()->m.survey(2));
        var wrong=new IslandPlan(m.reserved.building(),Y,8,6,16,IslandPlan.Surface.ROCK);assertThrows(IllegalStateException.class,()->new ColiseumRoute(m.layout,wrong,1,m));
        var invalid=new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.WATER,3);
        assertThrows(IllegalArgumentException.class,()->ColiseumLayout.inspect(parts.stream().map(Placed::piece).toList(),invalid,devices(parts)));
    }
    @Test void originalEntityTransformsAndRelocationRejectLostDuplicateOrWrongMobs() throws Exception {
        var templates=templates("coliseum");for(var r:Rotation.values())for(var e:Rotation.values()){
            var parts=assemble(templates,r,2,e);var layout=inspect(parts);var actual=entities(parts);assertTrue(layout.matchesEntities(actual));
            var missing=new ArrayList<>(actual);missing.remove(0);assertFalse(layout.matchesEntities(missing));
            var duplicate=new ArrayList<>(actual);duplicate.set(0,actual.get(1));assertFalse(layout.matchesEntities(duplicate));
            var wrong=new ArrayList<>(actual);wrong.set(0,new ColiseumLayout.Spawn(actual.get(0).pos(),"minecraft:zombie"));assertFalse(layout.matchesEntities(wrong));
            var moved=layout.move(4000,17,-3000);assertTrue(moved.matchesEntities(actual.stream().map(s->new ColiseumLayout.Spawn(s.pos().offset(4000,17,-3000),s.type())).toList()));
            assertEquals(layout.groundY()+17,moved.groundY());assertEquals(layout.spawner().pos().move(4000,17,-3000),moved.spawner().pos());assertEquals(layout.spawner().spawnData(),moved.spawner().spawnData());
        }
    }
    @Test void pinnedSparseTemplatesAndNativeDefinitionsRetainOriginalContent() throws Exception {
        assertEquals(HASH,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
        try(var zip=new ZipFile(JAR.toFile())){
            var def=json(zip,"data/dungeons_arise/worldgen/structure/coliseum.json");assertEquals(-3,def.getAsJsonObject("start_height").get("absolute").getAsInt());assertEquals("WORLD_SURFACE_WG",def.get("project_start_to_heightmap").getAsString());assertEquals("beard_box",def.get("terrain_adaptation").getAsString());
            var templates=templates("coliseum");assertEquals(6,templates.size());
            for(var t:templates.values()){
                if(t.id().startsWith("part_")){assertTrue(t.blocks().size()<32*32*32);assertTrue(t.blocks().stream().noneMatch(b->b.id().equals("minecraft:air")));}
                assertTrue(t.blocks().stream().noneMatch(b->b.data().contains("LootTable")),"Original coliseum has no container loot tables");
            }
            var enemy=templates.get("enemy_0").entities().getCompound(0).getCompound("nbt");assertEquals("minecraft:skeleton",enemy.getString("id"));assertFalse(enemy.getList("ArmorItems",Tag.TAG_COMPOUND).isEmpty());
        }
    }
}
