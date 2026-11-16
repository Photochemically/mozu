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

class MonasteryAuditTest {
    private static final WdaStructurePlanner.Environment ENV=new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.GRASS);
    private static List<Block> joints(Template t){return t.blocks().stream().filter(b->b.id().equals("minecraft:jigsaw")).toList();}
    private static Placed bridge(Placed parent,Block socket,Template child,boolean upperInlet){
        if(!upperInlet)return connect(parent,socket,child,Direction.WEST);
        // The native shuffled connector list can choose either same-name west inlet.
        var inlet=joints(child).stream().filter(b->b.pos().getY()==3&&b.data().getString("name").equals(socket.data().getString("target"))
                &&b.props().getString("orientation").equals("west_up")).findFirst().orElseThrow();
        var ordered=new ArrayList<>(child.blocks());ordered.remove(inlet);ordered.add(0,inlet);
        var selected=new Template(child.id(),child.resource(),child.pools(),child.legacy(),child.size(),ordered,child.entities());
        var p=connect(parent,socket,selected,Direction.WEST);return new Placed(child,p.origin(),p.rotation());
    }
    private static List<Placed> assemble(Map<String,Template> t,Rotation r,int variant){
        var root=new Placed(t.get("start"),new BlockPos(-113,Y-1,221),r);var parts=new ArrayList<Placed>();parts.add(root);int choices=variant;
        for(var s:joints(root.template())){
            boolean stairs=s.data().getString("target").endsWith("_1");var child=t.get(stairs?"room_3_stairs":"room_"+(choices%3));
            if(!stairs)choices/=3;parts.add(connect(root,s,child,Direction.DOWN));
        }
        var stair=parts.stream().filter(p->p.template().id().equals("room_3_stairs")).findFirst().orElseThrow();
        parts.add(connect(stair,stair.template().joint("roof",Direction.UP),t.get("top_0"),Direction.DOWN));
        var wing=parts.stream().filter(p->p.template().id().equals("room_1")).findFirst();
        if(wing.isPresent()){
            var room=wing.get();var crossing=connect(room,room.template().joint("monastery_corridors",Direction.NORTH),t.get("corridor_crossing"),Direction.WEST);parts.add(crossing);
            var hall=connect(crossing,crossing.template().joint("monastery_corridors",Direction.EAST),t.get("corridor_0"),Direction.WEST);parts.add(hall);
            parts.add(connect(hall,hall.template().joint("monastery_corridors",Direction.EAST),t.get("corridor_terminator"),Direction.WEST));
            var bridge=bridge(crossing,crossing.template().joint("monastery_bridges",Direction.SOUTH),t.get("bridge_crossing"),variant%2==0);parts.add(bridge);
            var span=connect(bridge,bridge.template().joint("monastery_bridges",Direction.EAST),t.get("bridge_0"),Direction.WEST);parts.add(span);
            parts.add(connect(span,span.template().joint("monastery_bridges",Direction.EAST),t.get("bridge_terminator"),Direction.WEST));
        }
        // Native jigsaw collision rejects overlapping external pieces; fixtures must obey it too.
        for(int i=0;i<parts.size();i++)for(int j=i+1;j<parts.size();j++)assertFalse(parts.get(i).bounds().intersects(parts.get(j).bounds()),
                "Overlapping monastery shell: "+parts.get(i).template().id()+" / "+parts.get(j).template().id());
        int decor=variant;
        for(var p:List.copyOf(parts))for(var s:joints(p.template())){
            String pool=s.data().getString("pool");
            if(pool.endsWith("monastery_corridors_deco"))parts.add(connect(p,s,t.get(decor++%5==4?"deco_empty":"corridors_deco_"+((decor-1)%5)),Direction.DOWN,Rotation.values()[variant%4]));
            if(pool.endsWith("monastery_bridges_deco"))parts.add(connect(p,s,t.get(decor++%2==0?"bridges_deco_0":"deco_empty"),Direction.DOWN));
        }
        for(var p:List.copyOf(parts))for(var s:joints(p.template()))if(s.data().getString("pool").endsWith("/villagers"))
            parts.add(connect(p,s,t.get(s.data().getString("target").endsWith("iron_golem")?"iron_golem_normal_0":"villager_normal_0"),Direction.DOWN,Rotation.values()[variant%4]));
        return parts;
    }
    private static List<MonasteryLayout.Socket> sockets(List<Placed> parts){
        var result=new ArrayList<MonasteryLayout.Socket>();for(int i=0;i<parts.size();i++){var p=parts.get(i);for(var b:joints(p.template())){
            var n=b.data();var axes=b.props().getString("orientation").split("_");result.add(new MonasteryLayout.Socket(i,p.at(b.pos()),p.rotation().rotate(Direction.byName(axes[0])),p.rotation().rotate(Direction.byName(axes[1])),
                    n.getString("name"),n.getString("target"),n.getString("pool"),n.getString("joint").equals("aligned")));
        }}return result;
    }
    private static List<MonasteryLayout.Feature> features(List<Placed> parts){
        var result=new ArrayList<MonasteryLayout.Feature>();for(var p:parts)for(var b:p.template().blocks())if(Set.of("minecraft:chest","minecraft:barrel","minecraft:water","minecraft:farmland","minecraft:wheat","minecraft:carrots","minecraft:potatoes","minecraft:beetroots").contains(b.id())){
            var pos=p.at(b.pos());result.add(new MonasteryLayout.Feature(new Cell(pos.getX(),pos.getY(),pos.getZ()),b.id(),b.data().getString("LootTable"),b.id().equals("minecraft:water")?Integer.parseInt(b.props().getString("level")):-1));
        }return result;
    }
    private static MonasteryLayout inspect(List<Placed> parts){return MonasteryLayout.inspect(parts.stream().map(Placed::piece).toList(),ENV,sockets(parts),features(parts));}
    private static List<MonasteryLayout.Spawn> entities(List<Placed> parts){
        var result=new ArrayList<MonasteryLayout.Spawn>();for(var p:parts)for(var raw:p.template().entities()){
            var n=(CompoundTag)raw;var pos=n.getList("pos",Tag.TAG_DOUBLE);var point=StructureTemplate.transform(new Vec3(pos.getDouble(0),pos.getDouble(1),pos.getDouble(2)),Mirror.NONE,p.rotation(),BlockPos.ZERO)
                    .add(p.origin().getX(),p.origin().getY(),p.origin().getZ());result.add(new MonasteryLayout.Spawn(BlockPos.containing(point),n.getCompound("nbt").getString("id")));
        }return result;
    }
    private static class Model extends Terrain implements MonasteryRoute.Probe {
        final MonasteryLayout layout;
        final IslandPlan reserved;
        final Map<Cell,Integer> scaffolds=new HashMap<>();
        Model(List<Placed> parts,IslandPlan.Surface surface){super(parts);layout=inspect(parts);reserved=new IslandPlan(island.building().expand(12),layout.groundY(),8,6,16,surface);}
        @Override public Type at(Cell p){
            if(blocked.contains(p))return Type.BLOCKED;
            var state=placed.get(p);if(state==null)return p.y()<layout.groundY()?Type.SUPPORT:Type.AIR;
            if(MonasteryRoute.gardenPlant(state.id()))return Type.PASSABLE;
            if(state.id().equals("minecraft:farmland"))return Type.SUPPORT;
            return super.at(p);
        }
        public boolean window(Cell p,Direction facing){var s=placed.get(p);return !blocked.contains(p)&&s!=null&&s.id().equals("minecraft:spruce_stairs")&&s.facing()==facing
                &&s.props().getString("half").equals("top")&&s.props().getString("shape").equals("straight")&&s.props().getString("waterlogged").equals("false");}
        public boolean feature(MonasteryLayout.Feature f){var s=placed.get(f.pos());return !blocked.contains(f.pos())&&s!=null&&s.id().equals(f.block())&&s.data().getString("LootTable").equals(f.loot())
                &&(!s.id().equals("minecraft:water")||Integer.parseInt(s.props().getString("level"))==f.waterLevel());}
        public boolean scaffold(Cell p,int distance){return !blocked.contains(p)&&Objects.equals(scaffolds.get(p),distance);}
        public void addScaffold(Cell p,int distance){
            if(scaffold(p,distance))return;assertEquals(Type.AIR,at(p));
            if(distance==0)assertTrue(at(p.move(0,-1,0))==Type.SUPPORT||scaffold(p.move(0,-1,0),0));
            else{assertEquals(1,distance);assertEquals(Type.AIR,at(p.move(0,-1,0)));assertTrue(List.of(p.move(1,0,0),p.move(-1,0,0),p.move(0,0,1),p.move(0,0,-1)).stream().anyMatch(q->scaffold(q,0)));}
            scaffolds.put(p,distance);placed.put(p,new State("minecraft:scaffolding",new CompoundTag(),null,new CompoundTag()));
        }
        void survey(int exits){var route=new MonasteryRoute(layout,reserved,exits,this);for(int i=0;i<60000;i++)if(route.step())return;fail("Monastery route budget exceeded");}
    }
    @Test void everyMainRoomCombinationKeepsOriginalWingsLootAndInhabitants() throws Exception {
        var templates=templates("monastery");var rows=new ArrayList<Map<String,Object>>();var seen=new TreeSet<String>();int windowCases=0;
        for(var surface:List.of(IslandPlan.Surface.GRASS,IslandPlan.Surface.ROCK))for(var rotation:Rotation.values())for(int variant=0;variant<27;variant++){
            // Immutable MapN uses linear probing; dense 3D record hashes collide heavily here.
            var parts=assemble(templates,rotation,variant);var m=new Model(parts,surface);var before=new HashMap<>(m.placed);
            for(int exits=1;exits<=4;exits++)try{m.survey(exits);}catch(RuntimeException e){throw new AssertionError("variant="+variant+", rotation="+rotation+", surface="+surface+", exits="+exits,e);}
            assertTrue(m.scaffolds.size()==0||m.scaffolds.size()==5);if(!m.scaffolds.isEmpty())windowCases++;
            if(variant==0)assertEquals(5,m.scaffolds.size());if(variant==13)assertTrue(m.scaffolds.isEmpty());
            for(var e:before.entrySet())if(!m.scaffolds.containsKey(e.getKey()))assertEquals(e.getValue(),m.placed.get(e.getKey()));
            for(var f:m.layout.features())assertTrue(m.feature(f));assertTrue(m.layout.matchesEntities(entities(parts)));assertEquals(Y+3,m.reserved.firstFreeY());
            for(var p:parts)seen.add(p.template().id());
            rows.add(Map.of("rotation",rotation,"variant",variant,"surface",surface,"pieces",parts.size(),"inhabitants",m.layout.inhabitants().size(),"preservedFeatures",m.layout.features().size(),"scaffoldBlocks",m.scaffolds.size(),"exitCounts",List.of(1,2,3,4),"bridgeInlet",parts.stream().anyMatch(p->p.template().id().equals("bridge_crossing"))?(variant%2==0?"upper":"lower"):"none"));
        }
        assertEquals(templates.keySet(),seen);var report=new LinkedHashMap<String,Object>();report.put("jarSha256",HASH);report.put("structure",MonasteryLayout.TARGET);report.put("cases",rows);report.put("sourceTemplates",seen);report.put("windowCases",windowCases);
        report.put("nativeGeneratorExecuted",false);report.put("runtimeTested",false);report.put("scope","216 original-template assemblies covering all 27 main room choices, four rotations, two dry surfaces and 1..4 exits. Native random generation and complete upper-floor exploration remain runtime checks.");
        Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/monastery-entry-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    @Test void coreMissingOrAnyAttachmentDetachedIsRejectedBeforeAllocation() throws Exception {
        var parts=assemble(templates("monastery"),Rotation.CLOCKWISE_90,13);
        for(int i=0;i<6;i++){var bad=new ArrayList<>(parts);bad.remove(i);assertThrows(IllegalArgumentException.class,()->inspect(bad));}
        for(int i=1;i<parts.size();i++){var bad=new ArrayList<>(parts);var p=bad.get(i);bad.set(i,new Placed(p.template(),p.origin().offset(1,0,0),p.rotation()));assertThrows(IllegalArgumentException.class,()->inspect(bad));}
        var duplicate=new ArrayList<>(parts);duplicate.add(parts.get(3));assertThrows(IllegalArgumentException.class,()->inspect(duplicate));
        var badAxes=new ArrayList<>(sockets(parts));var s=badAxes.get(0);badAxes.set(0,new MonasteryLayout.Socket(s.owner(),s.pos(),s.front(),s.top().getOpposite(),s.name(),s.target(),s.pool(),s.aligned()));
        assertThrows(IllegalArgumentException.class,()->MonasteryLayout.inspect(parts.stream().map(Placed::piece).toList(),ENV,badAxes,features(parts)));
    }
    @Test void blockedWindowExitOrChangedLootCannotBeErasedToMakeAPath() throws Exception {
        var parts=assemble(templates("monastery"),Rotation.NONE,0);
        var window=new Model(parts,IslandPlan.Surface.ROCK);window.blocked.add(window.layout.stairRoom().at(9,5,3));assertThrows(IllegalStateException.class,()->window.survey(1));assertTrue(window.scaffolds.isEmpty());
        var exit=new Model(parts,IslandPlan.Surface.ROCK);exit.blocked.add(exit.reserved.perimeterSites(4).get(3));assertThrows(IllegalStateException.class,()->exit.survey(4));assertTrue(exit.scaffolds.isEmpty());
        var loot=new Model(parts,IslandPlan.Surface.GRASS);loot.blocked.add(loot.layout.features().get(0).pos());assertThrows(IllegalStateException.class,()->loot.survey(1));assertTrue(loot.scaffolds.isEmpty());
        var invalid=new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.WATER,3);
        assertThrows(IllegalArgumentException.class,()->MonasteryLayout.inspect(parts.stream().map(Placed::piece).toList(),invalid,sockets(parts),features(parts)));
    }
    @Test void lateScaffoldObstructionFailsAndOriginalGroundRouteNeedsNoWindow() throws Exception {
        var templates=templates("monastery");var m=new Model(assemble(templates,Rotation.NONE,0),IslandPlan.Surface.GRASS);
        var route=new MonasteryRoute(m.layout,m.reserved,4,m);for(int i=0;m.scaffolds.isEmpty()&&i<60000;i++)assertFalse(route.step());assertEquals(1,m.scaffolds.size());
        var obstacle=m.layout.stairRoom().at(9,4,1);m.blocked.add(obstacle);assertThrows(IllegalStateException.class,route::step);assertEquals(1,m.scaffolds.size());
        var natural=new Model(assemble(templates,Rotation.NONE,13),IslandPlan.Surface.GRASS);natural.blocked.add(natural.layout.stairRoom().at(9,5,3));natural.survey(4);assertTrue(natural.scaffolds.isEmpty());
    }
    @Test void nativeEntityTransformsAndMovedWindowRetainRelativeGeometry() throws Exception {
        var templates=templates("monastery");for(var r:Rotation.values()){
            var parts=assemble(templates,r,17);var layout=inspect(parts);var actual=entities(parts);assertTrue(layout.matchesEntities(actual));
            var missing=new ArrayList<>(actual);missing.remove(0);assertFalse(layout.matchesEntities(missing));
            var duplicate=new ArrayList<>(actual);duplicate.set(0,actual.get(1));assertFalse(layout.matchesEntities(duplicate));
            var wrong=new ArrayList<>(actual);wrong.set(0,new MonasteryLayout.Spawn(actual.get(0).pos(),"minecraft:zombie"));assertFalse(layout.matchesEntities(wrong));
            var moved=layout.move(4000,13,-3000);assertTrue(moved.matchesEntities(actual.stream().map(s->new MonasteryLayout.Spawn(s.pos().offset(4000,13,-3000),s.type())).toList()));
            assertEquals(layout.stairRoom().at(9,4,2).move(4000,13,-3000),moved.stairRoom().at(9,4,2));assertEquals(layout.groundY()+13,moved.groundY());
        }
    }
    @Test void pinnedNativeStartRoomsAndNeutralResidentsRemainUnchanged() throws Exception {
        assertEquals(HASH,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
        try(var zip=new ZipFile(JAR.toFile())){
            var def=json(zip,"data/dungeons_arise/worldgen/structure/monastery.json");assertEquals(0,def.getAsJsonObject("start_height").get("absolute").getAsInt());assertEquals("WORLD_SURFACE_WG",def.get("project_start_to_heightmap").getAsString());assertEquals("beard_thin",def.get("terrain_adaptation").getAsString());
            var t=templates("monastery");assertEquals(20,t.size());assertEquals(4,t.get("start").blocks().size());
            for(var template:t.values()){assertTrue(template.blocks().stream().noneMatch(b->b.id().equals("minecraft:spawner")));for(var raw:template.entities())
                assertTrue(Set.of("minecraft:villager","minecraft:iron_golem").contains(((CompoundTag)raw).getCompound("nbt").getString("id")));}
        }
    }
    @Test void irrigationAndCropsRemainIntactAndAreNeverDryEntranceFootings() throws Exception {
        var parts=assemble(templates("monastery"),Rotation.NONE,26);var m=new Model(parts,IslandPlan.Surface.GRASS);
        var water=m.layout.features().stream().filter(f->f.block().equals("minecraft:water")).toList();assertFalse(water.isEmpty());
        assertTrue(water.stream().anyMatch(f->f.waterLevel()==1));
        assertTrue(m.layout.features().stream().anyMatch(f->f.block().equals("minecraft:farmland")));
        assertTrue(m.layout.features().stream().anyMatch(f->f.block().equals("minecraft:carrots")));
        for(var f:water)assertFalse(m.layout.approaches().contains(f.pos().move(0,1,0)));
        m.survey(4);for(var f:water)assertTrue(m.feature(f));
        var flowing=water.stream().filter(f->f.waterLevel()==1).findFirst().orElseThrow();var source=new Model(parts,IslandPlan.Surface.GRASS);
        var previous=source.placed.get(flowing.pos());var changed=previous.props().copy();changed.putString("level","0");
        source.placed.put(flowing.pos(),new State(previous.id(),changed,previous.facing(),previous.data()));
        assertThrows(IllegalStateException.class,()->source.survey(1));assertTrue(source.scaffolds.isEmpty());
        var corrupted=new Model(parts,IslandPlan.Surface.GRASS);corrupted.blocked.add(water.get(0).pos());
        assertThrows(IllegalStateException.class,()->corrupted.survey(1));assertTrue(corrupted.scaffolds.isEmpty());
    }
}
