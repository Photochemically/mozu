package dev.expedition.bridge.content.wda;

import com.google.gson.GsonBuilder;
import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.*;
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

class FortAuditTest {
    private static final BlockPos ORIGIN=new BlockPos(-113,Y-8,221);
    private static final WdaStructurePlanner.Environment ENV=new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.GRASS);
    private static List<Block> joints(Template t){return t.blocks().stream().filter(b->b.id().equals("minecraft:jigsaw")).toList();}
    private static Direction front(Block b){return Direction.byName(b.props().getString("orientation").split("_")[0]);}
    private static List<Placed> assemble(Map<String,Template> t,Rotation rotation,int variant,int root,boolean reverse) {
        var zero=new Placed(t.get("part_0"),ORIGIN,rotation);
        var one=connect(zero,zero.template().joint("illager_fort_side_2",Direction.EAST),t.get("part_1"),Direction.WEST);
        var three=connect(zero,zero.template().joint("illager_fort_side_1",Direction.SOUTH),t.get("part_3"),Direction.NORTH);
        var two=reverse?connect(three,three.template().joint("illager_fort_side_7",Direction.EAST),t.get("part_2"),Direction.WEST)
                :connect(one,one.template().joint("illager_fort_side_5",Direction.SOUTH),t.get("part_2"),Direction.NORTH);
        var shell=new ArrayList<>(List.of(zero,one,two,three));var parts=new ArrayList<>(shell);
        for(int i=0;i<4;i++) {
            var parent=shell.get(i);
            var socket=joints(parent.template()).stream().filter(b->b.data().getString("pool").equals(FortLayout.POOL+"top")).findFirst().orElseThrow();
            parts.add(connect(parent,socket,t.get("part_"+new int[]{4,5,7,6}[i]),Direction.DOWN));
        }
        int room=0;
        if(variant>=0) for(var parent:shell)for(var socket:joints(parent.template()))
            if(socket.data().getString("pool").equals(FortLayout.POOL+"rooms"))
                parts.add(connect(parent,socket,t.get("room_"+((variant+room++)%9)),Direction.DOWN));
        for(var parent:List.copyOf(parts)) for(var socket:joints(parent.template()))
            if(socket.data().getString("pool").equals(FortLayout.POOL+"spawners")) {
                var target=socket.data().getString("target").substring("dungeons_arise:illager_".length());
                parts.add(connect(parent,socket,t.get(target+"_0"),Direction.DOWN,Rotation.values()[Math.floorMod(variant,4)]));
            }
        Collections.swap(parts,0,root);return parts;
    }
    private static List<FortLayout.Socket> sockets(List<Placed> parts) {
        var list=new ArrayList<FortLayout.Socket>();
        for(int i=0;i<parts.size();i++){var p=parts.get(i);for(var b:joints(p.template())) {
            var n=b.data();var orientation=b.props().getString("orientation").split("_");
            list.add(new FortLayout.Socket(i,p.at(b.pos()),p.rotation().rotate(Direction.byName(orientation[0])),
                    p.rotation().rotate(Direction.byName(orientation[1])),n.getString("name"),n.getString("target"),n.getString("pool"),n.getString("joint").equals("aligned")));
        }}return list;
    }
    private static List<FortLayout.Feature> features(List<Placed> parts) {
        var list=new ArrayList<FortLayout.Feature>();
        for(var p:parts)for(var b:p.template().blocks())if(Set.of("minecraft:chest","minecraft:barrel","minecraft:spawner").contains(b.id())) {
            var pos=p.at(b.pos());list.add(new FortLayout.Feature(new Cell(pos.getX(),pos.getY(),pos.getZ()),b.id(),b.data().getString("LootTable")));
        }return list;
    }
    private static FortLayout inspect(List<Placed> parts){return FortLayout.inspect(parts.stream().map(Placed::piece).toList(),ENV,sockets(parts),features(parts));}
    private static class Model extends Terrain implements FortRoute.Probe {
        final FortLayout fort;
        final IslandPlan reserved;
        final Set<Cell> scaffold=new HashSet<>();
        Model(List<Placed> parts,IslandPlan.Surface surface) {
            super(parts);fort=inspect(parts);
            reserved=new IslandPlan(island.building().expand(12),Y,8,6,16,surface);
        }
        public boolean stair(Cell p,Direction facing) {
            var s=placed.get(p);return !blocked.contains(p)&&s!=null&&s.id().equals("minecraft:stone_brick_stairs")&&s.facing()==facing
                    &&s.props().getString("half").equals("bottom")&&s.props().getString("shape").equals("straight")&&s.props().getString("waterlogged").equals("false");
        }
        public boolean feature(FortLayout.Feature f) {var s=placed.get(f.pos());return !blocked.contains(f.pos())&&s!=null&&s.id().equals(f.block())&&s.data().getString("LootTable").equals(f.loot());}
        public boolean scaffold(Cell p){return !blocked.contains(p)&&scaffold.contains(p);}
        public void addScaffold(Cell p) {
            if(scaffold(p))return;
            assertEquals(Type.AIR,at(p));assertTrue(at(p.move(0,-1,0))==Type.SUPPORT||scaffold(p.move(0,-1,0)));
            assertFalse(island.building().contains(p.x(),p.y(),p.z()),"Scaffold must stay outside the native shell");
            scaffold.add(p);
            placed.put(p,new State("minecraft:scaffolding",new CompoundTag(),null,new CompoundTag()));
        }
        void survey(int exits) {var route=new FortRoute(fort,reserved,exits,this);for(int i=0;i<70000;i++)if(route.step())return;fail("Fort route exceeded model budget");}
    }

    @Test void nativeShellRoomsAndEncountersPreserveBothExteriorThresholdsAcrossRotations() throws Exception {
        var templates=templates("illager_fort");var rows=new ArrayList<Map<String,Object>>();var seen=new TreeSet<String>();
        for(var surface:List.of(IslandPlan.Surface.GRASS,IslandPlan.Surface.ROCK))for(var rotation:Rotation.values())for(int variant=0;variant<9;variant++) {
            int root=(variant+rotation.ordinal())%4;
            var parts=assemble(templates,rotation,variant,root,variant%2==0);var m=new Model(parts,surface);
            for(int exits=1;exits<=4;exits++)m.survey(exits);
            assertEquals(5,m.scaffold.size());assertEquals(15,parts.stream().filter(p->p.template().id().startsWith("room_")).count());
            for(var p:parts) {seen.add(p.template().id());for(var b:p.template().blocks())if(b.data().contains("LootTable")||b.id().equals("minecraft:spawner")) {
                var pos=p.at(b.pos());assertEquals(b.data(),m.placed.get(new Cell(pos.getX(),pos.getY(),pos.getZ())).data());
            }}
            var row=new LinkedHashMap<String,Object>();row.put("rotation",rotation);row.put("surface",surface);row.put("root",root);row.put("roomVariantCycle",variant);
            row.put("pieces",parts.size());row.put("lootAndDevices",m.fort.features().size());row.put("inhabitants",m.fort.inhabitants().size());row.put("exitCounts",List.of(1,2,3,4));rows.add(row);
        }
        assertEquals(templates.keySet(),seen);
        var report=new LinkedHashMap<String,Object>();report.put("jarSha256",HASH);report.put("structure",FortLayout.TARGET);report.put("cases",rows);
        report.put("sourceTemplates",seen);report.put("nativeGeneratorExecuted",false);report.put("runtimeTested",false);
        report.put("scope","72 hand-assembled complete native shells with 15 optional room slots and original encounters; four root choices and rotations; entrance reaches the original balcony doorway, upper/lower-room exploration remains a runtime check");
        Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/fort-entry-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    @Test void blockedSideUsesOtherOriginalThresholdAndBlockedBothSidesWriteNothing() throws Exception {
        var parts=assemble(templates("illager_fort"),Rotation.NONE,0,0,false);
        for(int blockedSide=0;blockedSide<2;blockedSide++){
            var m=new Model(parts,IslandPlan.Surface.ROCK);m.blocked.add(m.fort.side(blockedSide,8,12,24));m.survey(4);
            assertTrue(m.scaffold.contains(m.fort.side(1-blockedSide,-1,8,24)));
        }
        var both=new Model(parts,IslandPlan.Surface.GRASS);
        for(int side=0;side<2;side++)both.blocked.add(both.fort.side(side,8,12,24));
        assertThrows(IllegalStateException.class,()->both.survey(1));assertTrue(both.scaffold.isEmpty());
    }
    @Test void blockedExitLootOrScaffoldSupportRejectWithoutErasingOriginalBlocks() throws Exception {
        var parts=assemble(templates("illager_fort"),Rotation.CLOCKWISE_90,2,1,false);
        var exit=new Model(parts,IslandPlan.Surface.GRASS);exit.blocked.add(exit.reserved.perimeterSites(4).get(3));
        assertThrows(IllegalStateException.class,()->exit.survey(4));assertTrue(exit.scaffold.isEmpty());
        var loot=new Model(parts,IslandPlan.Surface.GRASS);loot.blocked.add(loot.fort.features().get(0).pos());
        assertThrows(IllegalStateException.class,()->loot.survey(1));assertTrue(loot.scaffold.isEmpty());
        var footing=new Model(parts,IslandPlan.Surface.GRASS);for(int side=0;side<2;side++)footing.blocked.add(footing.fort.side(side,-1,7,24));
        assertThrows(IllegalStateException.class,()->footing.survey(1));assertTrue(footing.scaffold.isEmpty());
    }
    @Test void incompleteMisplacedDetachedOrMisorientedPiecesAreRejectedBeforeAllocation() throws Exception {
        var parts=assemble(templates("illager_fort"),Rotation.COUNTERCLOCKWISE_90,3,2,true);
        for(int i=0;i<8;i++){var bad=new ArrayList<>(parts);bad.remove(i);assertThrows(IllegalArgumentException.class,()->inspect(bad));}
        var duplicate=new ArrayList<>(parts);duplicate.add(parts.get(1));assertThrows(IllegalArgumentException.class,()->inspect(duplicate));
        for(int index:List.of(1,8,parts.size()-1)) {
            var p=parts.get(index);var bad=new ArrayList<>(parts);bad.set(index,new Placed(p.template(),p.origin().offset(1,0,0),p.rotation()));
            assertThrows(IllegalArgumentException.class,()->inspect(bad));
        }
        var wrongSockets=new ArrayList<>(sockets(parts));
        int roomIndex=8;
        for(int i=0;i<wrongSockets.size();i++){var s=wrongSockets.get(i);if(s.owner()==roomIndex && s.front()==Direction.DOWN)
            wrongSockets.set(i,new FortLayout.Socket(s.owner(),s.pos(),s.front(),s.top().getOpposite(),s.name(),s.target(),s.pool(),s.aligned()));}
        assertThrows(IllegalArgumentException.class,()->FortLayout.inspect(parts.stream().map(Placed::piece).toList(),ENV,wrongSockets,features(parts)));
    }
    @Test void entitiesAndEntranceGeometryTranslateWithTheSavedInstance() throws Exception {
        var templates=templates("illager_fort");
        for(var rotation:Rotation.values()) {
            var parts=assemble(templates,rotation,5,3,false);var fort=inspect(parts);var entities=new ArrayList<FortLayout.Spawn>();
            for(var p:parts)for(var raw:p.template().entities()) {
                var e=(CompoundTag)raw;var v=e.getList("pos",Tag.TAG_DOUBLE);
                var transformed=StructureTemplate.transform(new Vec3(v.getDouble(0),v.getDouble(1),v.getDouble(2)),Mirror.NONE,p.rotation(),BlockPos.ZERO)
                        .add(p.origin().getX(),p.origin().getY(),p.origin().getZ());
                entities.add(new FortLayout.Spawn(BlockPos.containing(transformed),e.getCompound("nbt").getString("id")));
            }
            assertTrue(fort.matchesEntities(entities));assertFalse(fort.matchesEntities(entities.subList(1,entities.size())));
            var duplicate=new ArrayList<>(entities);duplicate.add(entities.get(0));assertFalse(fort.matchesEntities(duplicate));
            var wrong=new ArrayList<>(entities);wrong.set(0,new FortLayout.Spawn(entities.get(0).pos(),"minecraft:pig"));assertFalse(fort.matchesEntities(wrong));
            var moved=fort.move(31,17,-91);assertEquals(fort.side(1,-1,8,24).move(31,17,-91),moved.side(1,-1,8,24));
            assertTrue(moved.matchesEntities(entities.stream().map(e->new FortLayout.Spawn(e.pos().offset(31,17,-91),e.type())).toList()));
            assertFalse(moved.matchesEntities(entities));
            assertEquals(fort.features().get(0).pos().move(31,17,-91),moved.features().get(0).pos());
        }
    }
    @Test void nativePoolWeightsAndAllTwentyOneTemplatesRemainUnmodified() throws Exception {
        assertEquals(HASH,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
        var t=templates("illager_fort");assertEquals(21,t.size());
        try(var zip=new ZipFile(JAR.toFile())) {
            String wg="data/dungeons_arise/worldgen/";var def=json(zip,wg+"structure/illager_fort.json");
            assertEquals(FortLayout.POOL+"main",def.get("start_pool").getAsString());assertEquals(-7,def.getAsJsonObject("start_height").get("absolute").getAsInt());
            assertEquals("WORLD_SURFACE_WG",def.get("project_start_to_heightmap").getAsString());assertEquals("beard_thin",def.get("terrain_adaptation").getAsString());
            assertEquals(7,def.get("size").getAsInt());assertEquals(114,def.get("max_distance_from_center").getAsInt());
            for(var name:List.of("main","top","rooms","spawners")) {
                var p=json(zip,wg+"template_pool/fortified/illager_fort/illager_fort_"+name+".json");
                assertEquals("minecraft:empty",p.get("fallback").getAsString());var entries=p.getAsJsonArray("elements");
                assertEquals(name.equals("rooms")?9:4,entries.size());int weight=0;
                for(var raw:entries) {var e=raw.getAsJsonObject();weight+=e.get("weight").getAsInt();var el=e.getAsJsonObject("element");
                    assertEquals("rigid",el.get("projection").getAsString());assertEquals("minecraft:empty",el.get("processors").getAsString());
                    assertEquals("minecraft:single_pool_element",el.get("element_type").getAsString());}
                assertEquals(name.equals("spawners")?6:name.equals("rooms")?9:4,weight);
            }
        }
        // Room air is explicit: retain it so native placement can clear its underground interiors.
        for(int i=0;i<9;i++){var r=t.get("room_"+i);assertEquals(1134,r.blocks().size());assertTrue(r.blocks().stream().filter(b->b.id().equals("minecraft:air")).count()>700);}
    }
}
