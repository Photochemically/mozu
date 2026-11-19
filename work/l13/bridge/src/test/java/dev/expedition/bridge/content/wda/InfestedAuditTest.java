package dev.expedition.bridge.content.wda;

import com.google.gson.GsonBuilder;
import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import static dev.expedition.bridge.content.wda.WdaTemplateFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class InfestedAuditTest {
    private static final WdaStructurePlanner.Environment ENV=new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.ROCK);
    private static List<Block> joints(Placed p){return p.template().blocks().stream().filter(b->b.id().equals("minecraft:jigsaw")).toList();}
    private static Direction front(Block b){return Direction.byName(b.props().getString("orientation").split("_")[0]);}
    private static Block side(Placed p,Direction d){return joints(p).stream().filter(b->front(b)==d).findFirst().orElseThrow();}
    private static List<Placed> assemble(Map<String,Template> t,Rotation r,int variant){
        var root=new Placed(t.get("layout_part_1_middle"),new BlockPos(-113,Y+53,221),r);
        var lower=connect(root,side(root,Direction.DOWN),t.get("layout_part_2_middle"),Direction.UP);
        var bottom=connect(lower,side(lower,Direction.DOWN),t.get("layout_part_3_base"),Direction.UP);
        var top=connect(root,root.template().joint("infested_temple_layout_side_0",Direction.UP),t.get("layout_part_0_top"),Direction.DOWN);
        var parts=new ArrayList<>(List.of(root,lower,bottom,top));var interiors=new ArrayList<Placed>();
        int tier=0;
        for(var layout:List.of(bottom,lower,root,top)){
            String group=tier==0?"base":tier==3?"top":"main";
            var corner=joints(layout).stream().filter(b->front(b)==Direction.UP&&b.data().getString("target").contains("layout_"+(group.equals("main")?"middle":group)+"_1")).findFirst().orElseThrow();
            var one=connect(layout,corner,t.get(group+"_part_1"),Direction.DOWN);
            var zero=connect(layout,side(layout,Direction.EAST),t.get(group+"_part_0"),Direction.WEST);
            var two=connect(zero,side(zero,Direction.EAST),t.get(group+"_part_2"),Direction.WEST);
            var three=connect(two,side(two,Direction.SOUTH),t.get(group+"_part_3"),Direction.NORTH);
            var four=connect(three,side(three,Direction.SOUTH),t.get(group+"_part_4"),Direction.NORTH);
            var five=connect(four,side(four,Direction.WEST),t.get(group+"_part_5"),Direction.EAST);
            var six=connect(five,side(five,Direction.WEST),t.get(group+"_part_6"),Direction.EAST);
            var seven=variant%2==0?connect(layout,side(layout,Direction.SOUTH),t.get(group+"_part_7"),Direction.NORTH)
                    :connect(six,side(six,Direction.NORTH),t.get(group+"_part_7"),Direction.SOUTH);
            parts.addAll(List.of(zero,one,two,three,four,five,six,seven));
            var inner=connect(zero,side(zero,Direction.SOUTH),t.get(tier==3?"inner_top_0":"inner_part_0"),Direction.NORTH);parts.add(inner);interiors.add(inner);tier++;
        }
        int floor=0;
        for(var inner:interiors)for(var socket:joints(inner))if(socket.data().getString("pool").equals(InfestedLayout.POOL+"levels"))
            parts.add(connect(inner,socket,t.get("level_"+((floor++ + variant)%3)),Direction.DOWN));
        int room=0;
        for(var level:List.copyOf(parts))if(level.template().id().startsWith("level_"))for(var socket:joints(level))if(socket.data().getString("pool").equals(InfestedLayout.POOL+"rooms")){
            parts.add(connect(level,socket,t.get("room_"+((room+variant)%9)),Direction.DOWN,Rotation.values()[(room+variant)%4]));room++;
        }
        int encounter=0;
        for(var p:List.copyOf(parts))for(var socket:joints(p))if(front(socket)==Direction.UP&&socket.data().getString("pool").equals(InfestedLayout.POOL+"spawners")){
            var target=socket.data().getString("target");String id=target.endsWith("wither_skeleton")?"wither_skeleton_0":target.endsWith("skeleton")?"skeleton_0":"cave_spider_"+((variant+encounter)%2);
            parts.add(connect(p,socket,t.get(id),Direction.DOWN));encounter++;
        }
        return parts;
    }
    private static List<InfestedLayout.Socket> sockets(List<Placed> parts){
        var result=new ArrayList<InfestedLayout.Socket>();for(int i=0;i<parts.size();i++){var p=parts.get(i);for(var b:joints(p)){
            var n=b.data();var d=b.props().getString("orientation").split("_");result.add(new InfestedLayout.Socket(i,p.at(b.pos()),p.rotation().rotate(Direction.byName(d[0])),p.rotation().rotate(Direction.byName(d[1])),n.getString("name"),n.getString("target"),n.getString("pool"),n.getString("joint").equals("aligned")));
        }}return result;
    }
    private static List<InfestedLayout.Feature> features(List<Placed> parts){
        var result=new ArrayList<InfestedLayout.Feature>();for(var p:parts)for(var b:p.template().blocks())if(Set.of("minecraft:chest","minecraft:barrel","minecraft:spawner").contains(b.id())){
            var pos=p.at(b.pos());result.add(new InfestedLayout.Feature(new Cell(pos.getX(),pos.getY(),pos.getZ()),b.id(),InfestedLayout.stableData(b.id(),b.data())));
        }return result;
    }
    private static InfestedLayout inspect(List<Placed> p){return InfestedLayout.inspect(p.stream().map(Placed::piece).toList(),ENV,sockets(p),features(p));}
    private static class Model extends Terrain implements InfestedRoute.Probe {
        final InfestedLayout layout;
        final IslandPlan reserved;
        int checked;
        Model(List<Placed> parts,IslandPlan.Surface s){super(parts);layout=inspect(parts);reserved=new IslandPlan(island.building(),layout.groundY(),8,6,16,s);}
        @Override public Type at(Cell p){if(blocked.contains(p))return Type.BLOCKED;if(!placed.containsKey(p))return p.y()<layout.groundY()?Type.SUPPORT:Type.AIR;return super.at(p);}
        public boolean stair(Cell p,Direction d){var s=placed.get(p);return !blocked.contains(p)&&s!=null&&s.id().equals("minecraft:spruce_stairs")&&s.facing()==d
                &&s.props().getString("half").equals("bottom")&&s.props().getString("shape").equals("straight")&&s.props().getString("waterlogged").equals("false");}
        public boolean feature(InfestedLayout.Feature f){checked++;var s=placed.get(f.pos());return !blocked.contains(f.pos())&&s!=null&&s.id().equals(f.block())&&InfestedLayout.stableData(s.id(),s.data()).equals(f.data());}
        void finish(InfestedRoute route){for(int i=0;i<30000;i++)if(route.step())return;fail("Infested route exceeded budget");}
        void survey(int exits){finish(new InfestedRoute(layout,reserved,exits,this));}
    }
    @Test void completeOriginalTowerFloorsRoomsAndEncountersPreserveLowerHallAccess() throws Exception {
        var templates=templates("infested_temple");var rows=new ArrayList<Map<String,Object>>();var seen=new TreeSet<String>();
        for(var r:Rotation.values())for(var surface:List.of(IslandPlan.Surface.GRASS,IslandPlan.Surface.ROCK))for(int variant=0;variant<3;variant++){
            var parts=assemble(templates,r,variant);var m=new Model(parts,surface);var before=new HashMap<>(m.placed);
            for(int exits=1;exits<=4;exits++)m.survey(exits);
            assertEquals(before,m.placed);assertEquals(Y-10,m.reserved.firstFreeY());assertEquals(63,m.reserved.building().width());assertEquals(63,m.reserved.building().depth());assertEquals(128,m.reserved.building().height());
            assertEquals(12,parts.stream().filter(p->p.template().id().startsWith("level_")).count());assertEquals(32,parts.stream().filter(p->p.template().id().startsWith("room_")).count());
            assertEquals(m.layout.features().size()*4,m.checked);for(var p:parts)seen.add(p.template().id());
            rows.add(Map.of("rotation",r,"surface",surface,"floorVariantCycle",variant,"pieces",parts.size(),"floors",12,"rooms",32,"containersAndSpawners",m.layout.features().size(),"islandFirstFreeY",m.reserved.firstFreeY(),"exitCounts",List.of(1,2,3,4)));
        }
        assertEquals(templates.keySet(),seen);var report=new LinkedHashMap<String,Object>();report.put("jarSha256",HASH);report.put("structure",InfestedLayout.TARGET);report.put("cases",rows);report.put("sourceTemplates",seen);
        report.put("nativeGeneratorExecuted",false);report.put("runtimeTested",false);report.put("scope","24 complete original-template assemblies with four shell tiers, twelve floors, 32 selected rooms and original spawners. All 46 templates, four rotations, three floor cycles, two surfaces and 1..4 exits. Static route reaches the original lower outer hall; native generator and full interior/upper traversal remain runtime checks.");
        Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/infested-entry-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    @Test void missingCoreOrSelectedRoomAndEncounterCannotBeSilentlyOmitted() throws Exception {
        var parts=assemble(templates("infested_temple"),Rotation.NONE,0);
        for(String pattern:List.of("layout_part_0_top","base_part_4","main_part_4","top_part_4","inner_part_0","inner_top_0","level_0","room_0","cave_spider_0")){
            var bad=new ArrayList<>(parts);assertTrue(bad.removeIf(p->p.template().id().equals(pattern)));assertThrows(IllegalArgumentException.class,()->inspect(bad),pattern);
        }
        var duplicate=new ArrayList<>(parts);duplicate.add(parts.get(parts.size()-1));assertThrows(IllegalArgumentException.class,()->inspect(duplicate));
    }
    @Test void displacedFloorsRoomsAndWrongAlignedEncounterFacingReject() throws Exception {
        var parts=assemble(templates("infested_temple"),Rotation.CLOCKWISE_90,1);
        for(String id:List.of("layout_part_3_base","base_part_0","inner_top_0","level_1","room_1","skeleton_0")){
            var bad=new ArrayList<>(parts);int i=0;while(!bad.get(i).template().id().equals(id))i++;var p=bad.get(i);bad.set(i,new Placed(p.template(),p.origin().offset(1,0,0),p.rotation()));assertThrows(IllegalArgumentException.class,()->inspect(bad),id);
        }
        var bad=new ArrayList<>(sockets(parts));int i=0;while(!(bad.get(i).front()==Direction.UP&&bad.get(i).pool().equals(InfestedLayout.POOL+"spawners")))i++;
        var s=bad.get(i);bad.set(i,new InfestedLayout.Socket(s.owner(),s.pos(),s.front(),s.top().getOpposite(),s.name(),s.target(),s.pool(),s.aligned()));
        assertThrows(IllegalArgumentException.class,()->InfestedLayout.inspect(parts.stream().map(Placed::piece).toList(),ENV,bad,features(parts)));
    }
    @Test void originalStepsHallAndEveryRequestedExitAreProtectedFromObstruction() throws Exception {
        var parts=assemble(templates("infested_temple"),Rotation.NONE,0);
        for(int x:new int[]{4,5,9}){var m=new Model(parts,IslandPlan.Surface.ROCK);m.blocked.add(m.layout.at(x,x==4?0:x==5?1:2,31));assertThrows(IllegalStateException.class,()->m.survey(1));}
        var exit=new Model(parts,IslandPlan.Surface.GRASS);exit.blocked.add(exit.reserved.perimeterSites(4).get(3));assertThrows(IllegalStateException.class,()->exit.survey(4));
        var late=new Model(parts,IslandPlan.Surface.ROCK);var route=new InfestedRoute(late.layout,late.reserved,1,late);for(int i=0;late.checked==0&&i<30000;i++)assertFalse(route.step());assertEquals(1,late.checked);
        late.blocked.add(late.layout.at(9,2,31));assertThrows(IllegalStateException.class,()->late.finish(route));
    }
    @Test void sourceLootSpawnDataPotentialsAndSpawnSettingsArePreserved() throws Exception {
        var parts=assemble(templates("infested_temple"),Rotation.NONE,2);var m=new Model(parts,IslandPlan.Surface.ROCK);
        var device=m.layout.features().stream().filter(f->f.block().equals("minecraft:spawner")).findFirst().orElseThrow();var original=m.placed.get(device.pos());
        for(String field:List.of("SpawnData","SpawnPotentials","SpawnCount","MinSpawnDelay","MaxSpawnDelay","SpawnRange","MaxNearbyEntities","RequiredPlayerRange")){
            var n=original.data().copy();if(field.equals("SpawnData"))n.getCompound(field).getCompound("entity").putString("id","minecraft:pig");else if(field.equals("SpawnPotentials"))n.remove(field);else n.putInt(field,n.getInt(field)+1);
            m.placed.put(device.pos(),new State(original.id(),original.props(),original.facing(),n));assertThrows(IllegalStateException.class,()->m.survey(1),field);
        }
        var timer=original.data().copy();timer.putShort("Delay",(short)137);m.placed.put(device.pos(),new State(original.id(),original.props(),original.facing(),timer));m.survey(1);
        var chest=m.layout.features().stream().filter(f->f.block().equals("minecraft:chest")).findFirst().orElseThrow();var old=m.placed.get(chest.pos());var n=old.data().copy();
        n.putLong("LootTableSeed",922337203685477000L);m.placed.put(chest.pos(),new State(old.id(),old.props(),old.facing(),n));m.survey(1);
        n.putString("LootTable","minecraft:chests/simple_dungeon");
        m.placed.put(chest.pos(),new State(old.id(),old.props(),old.facing(),n));assertThrows(IllegalStateException.class,()->m.survey(1));
    }
    @Test void nativeHeightEnvironmentAndRelocationKeepCompleteGeometry() throws Exception {
        var parts=assemble(templates("infested_temple"),Rotation.COUNTERCLOCKWISE_90,0);var m=new Model(parts,IslandPlan.Surface.ROCK);var moved=m.layout.move(4000,17,-3000);
        assertEquals(m.layout.at(4,0,31).move(4000,17,-3000),moved.at(4,0,31));assertEquals(m.layout.groundY()+17,moved.groundY());
        for(int i=0;i<m.layout.features().size();i++){assertEquals(m.layout.features().get(i).pos().move(4000,17,-3000),moved.features().get(i).pos());assertEquals(m.layout.features().get(i).data(),moved.features().get(i).data());}
        assertThrows(IllegalStateException.class,()->new InfestedRoute(m.layout,new IslandPlan(m.reserved.building(),Y,8,6,16,IslandPlan.Surface.ROCK),1,m));
        assertThrows(IllegalArgumentException.class,()->InfestedLayout.inspect(parts.stream().map(Placed::piece).toList(),new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.WATER,3),sockets(parts),features(parts)));
        assertEquals(HASH,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
        try(var zip=new ZipFile(JAR.toFile())){var def=json(zip,"data/dungeons_arise/worldgen/structure/infested_temple.json");assertEquals(54,def.getAsJsonObject("start_height").get("absolute").getAsInt());assertEquals("WORLD_SURFACE_WG",def.get("project_start_to_heightmap").getAsString());assertFalse(def.has("terrain_adaptation"));}
        var t=templates("infested_temple");assertEquals(46,t.size());assertTrue(t.values().stream().allMatch(v->v.entities().isEmpty()));
        var spider=t.get("cave_spider_1").blocks().stream().filter(b->b.id().equals("minecraft:spawner")).findFirst().orElseThrow().data().getCompound("SpawnData").getCompound("entity");
        assertEquals("minecraft:spider",spider.getString("id"));assertEquals("minecraft:cave_spider",spider.getList("Passengers",10).getCompound(0).getString("id"));
    }
}
