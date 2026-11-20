package dev.expedition.bridge.content.wda;

import com.google.gson.GsonBuilder;
import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import static dev.expedition.bridge.content.wda.WdaTemplateFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class ThornbornAuditTest {
    static final WdaStructurePlanner.Environment ENV=new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.ROCK);
    private static Map<String,Template> cached;
    static Map<String,Template> source() throws Exception {
        if(cached!=null)return cached;var t=new HashMap<>(templates("thornborn_towers"));
        // Production WdaAliasData serves this original file under the pool's missing resource name.
        var original=t.remove("hanging_bridge_3_medium_terminator");String key="hanging_bridge_2_medium_terminator";
        t.put(key,new Template(key,ThornbornLayout.PREFIX+key,Set.of(ThornbornLayout.POOL+"hanging",ThornbornLayout.POOL+"terminators"),false,original.size(),original.blocks(),original.entities()));
        for(var a:t.values()){assertTrue(a.entities().isEmpty());assertTrue(a.blocks().stream().noneMatch(b->Set.of("minecraft:water","minecraft:lava","minecraft:dispenser","minecraft:tnt").contains(b.id())));}
        assertEquals(34,t.size());cached=Map.copyOf(t);return cached;
    }
    static List<Block> joints(Template t){return t.blocks().stream().filter(b->b.id().equals("minecraft:jigsaw")).toList();}
    static Direction front(Block b){return Direction.byName(b.props().getString("orientation").split("_")[0]);}
    static Placed attach(Placed parent,Block socket,Template child,Direction inlet){
        assertTrue(ThornbornLayout.accepts(socket.data().getString("pool"),child.id()));var pools=new HashSet<>(child.pools());pools.add(socket.data().getString("pool"));
        return connect(parent,socket,new Template(child.id(),child.resource(),pools,false,child.size(),child.blocks(),child.entities()),inlet);
    }
    static Placed add(List<Placed> parts,Placed p,String target,Direction out,Template child,Direction in){var result=attach(p,p.template().joint(target,out),child,in);parts.add(result);return result;}
    static boolean overlaps(Placed a,Placed b){var x=a.bounds();var y=b.bounds();return x.minX()<=y.maxX()&&x.maxX()>=y.minX()&&x.minY()<=y.maxY()&&x.maxY()>=y.minY()&&x.minZ()<=y.maxZ()&&x.maxZ()>=y.minZ();}
    static List<Placed> assemble(Rotation rotation,int variant) throws Exception {
        var t=source();var parts=new ArrayList<Placed>();var root=new Placed(t.get("part_0_main"),new BlockPos(-113,129,221),rotation);parts.add(root);
        var bridge=add(parts,root,"thornborn_towers_bridge",Direction.EAST,t.get("bridge_0"),Direction.WEST);
        var second=add(parts,bridge,"thornborn_towers_main_middle",Direction.EAST,t.get("part_0_main"),Direction.WEST);
        add(parts,root,"thornborn_towers_bridge",Direction.WEST,t.get(variant%2==0?"bridge_1_broken":"bridge_2_terminator"),Direction.EAST);
        add(parts,second,"thornborn_towers_bridge",Direction.EAST,t.get(variant%2==0?"bridge_2_terminator":"bridge_1_broken"),Direction.EAST);
        int v=variant;
        for(var main:List.of(root,second)){
            add(parts,main,"thornborn_towers_main_top",Direction.UP,t.get("roof_"+(v++%2)),Direction.DOWN);
            var below=add(parts,main,"thornborn_towers_main_support",Direction.DOWN,t.get("part_1_support"),Direction.UP);
            for(int level=0;level<=variant%3;level++)below=add(parts,below,"thornborn_towers_main_tower",Direction.DOWN,t.get("part_2_tower"),Direction.UP);
        }
        var hanging=add(parts,root,"thornborn_towers_hanging_big",Direction.NORTH,t.get("hanging_big_0_main"),Direction.WEST);
        add(parts,hanging,"thornborn_towers_hanging_big_tower",Direction.DOWN,t.get("hanging_big_bottom"),Direction.UP);
        var tower=add(parts,hanging,"thornborn_towers_hanging_big_tower",Direction.UP,t.get("hanging_big_0_tower"),Direction.DOWN);
        add(parts,tower,"thornborn_towers_hanging_big_tower",Direction.UP,t.get("hanging_big_top"),Direction.DOWN);
        var medium=add(parts,hanging,"thornborn_towers_hanging_medium",Direction.NORTH,t.get("hanging_medium_"+(variant%3)),Direction.NORTH);
        for(var d:List.of(Direction.SOUTH,Direction.EAST))add(parts,hanging,"thornborn_towers_hanging_medium",d,t.get("hanging_bridge_2_medium_terminator"),Direction.WEST);
        // Continuing this bend with a medium tower would collide with the original broken bridge.
        if(variant%3==2)add(parts,medium,"thornborn_towers_hanging_medium",Direction.EAST,t.get("hanging_bridge_2_medium_terminator"),Direction.WEST);
        int n=variant;
        for(var joint:joints(medium.template()))if(joint.data().getString("target").endsWith("_hanging_small_bridge")){
            String key=switch(n++%4){case 0->"hanging_bridge_0_small";case 1->"hanging_small_1";case 2->"hanging_small_2";default->"hanging_bridge_1_small_terminator";};
            var small=attach(medium,joint,t.get(key),Direction.NORTH);if(parts.stream().anyMatch(p->overlaps(p,small)))continue;parts.add(small);
            if(key.equals("hanging_bridge_0_small")){
                var room=add(parts,small,"thornborn_towers_hanging_small",Direction.SOUTH,t.get("hanging_small_0"),Direction.NORTH);
                for(var out:joints(room.template()))if(front(out)!=Direction.NORTH){var cap=attach(room,out,t.get("hanging_bridge_1_small_terminator"),Direction.NORTH);if(parts.stream().noneMatch(p->overlaps(p,cap)))parts.add(cap);}
            }
        }
        // Structural pieces must be non-overlapping; rooms and encounters legitimately occupy their parent interiors.
        for(int i=0;i<parts.size();i++)for(int k=i+1;k<parts.size();k++)assertFalse(overlaps(parts.get(i),parts.get(k)),parts.get(i).template().id()+" / "+parts.get(k).template().id());
        for(var p:List.copyOf(parts))for(var b:joints(p.template()))if(b.data().getString("pool").equals(ThornbornLayout.POOL+"rooms"))parts.add(attach(p,b,t.get("room_"+(v++%6)),Direction.DOWN));
        for(var p:List.copyOf(parts))for(var b:joints(p.template()))if(front(b)==Direction.UP&&b.data().getString("pool").equals(ThornbornLayout.POOL+"spawners")){
            var options=t.values().stream().filter(a->ThornbornLayout.ENCOUNTERS.contains(a.id())&&joints(a).get(0).data().getString("name").equals(b.data().getString("target"))).sorted(Comparator.comparing(Template::id)).toList();
            parts.add(attach(p,b,options.get(v++%options.size()),Direction.DOWN));
        }
        return parts;
    }
    static List<ThornbornLayout.Socket> sockets(List<Placed> parts){var result=new ArrayList<ThornbornLayout.Socket>();for(int i=0;i<parts.size();i++){var p=parts.get(i);for(var b:joints(p.template())){var n=b.data();var axes=b.props().getString("orientation").split("_");result.add(new ThornbornLayout.Socket(i,p.at(b.pos()),p.rotation().rotate(Direction.byName(axes[0])),p.rotation().rotate(Direction.byName(axes[1])),n.getString("name"),n.getString("target"),n.getString("pool"),n.getString("joint").equals("aligned")));}}return result;}
    static List<ThornbornLayout.Feature> features(List<Placed> parts){var result=new ArrayList<ThornbornLayout.Feature>();for(var p:parts)for(var b:p.template().blocks())if(Set.of("minecraft:chest","minecraft:barrel","minecraft:spawner").contains(b.id())){var at=p.at(b.pos());result.add(new ThornbornLayout.Feature(new Cell(at.getX(),at.getY(),at.getZ()),b.id(),ThornbornLayout.stableData(b.id(),b.data())));}return result;}
    static ThornbornLayout inspect(List<Placed> p){return ThornbornLayout.inspect(p.stream().map(Placed::piece).toList(),ENV,sockets(p),features(p));}
    static class Model extends Terrain implements ThornbornRoute.Probe {
        final ThornbornLayout layout;final IslandPlan reserved;final Set<Cell> scaffolds=new HashSet<>();int checks;
        Model(List<Placed> p,IslandPlan.Surface surface){super(p);layout=inspect(p);reserved=new IslandPlan(island.building(),layout.groundY(),8,6,16,surface);}
        @Override public Type at(Cell p){if(blocked.contains(p))return Type.BLOCKED;if(!placed.containsKey(p))return p.y()<layout.groundY()?Type.SUPPORT:Type.AIR;return super.at(p);}
        public boolean bottomSlab(Cell p){var s=placed.get(p);return !blocked.contains(p)&&s!=null&&s.id().equals("minecraft:dark_oak_slab")&&s.props().getString("type").equals("bottom")&&!s.props().getString("waterlogged").equals("true");}
        public boolean scaffold(Cell p){return scaffolds.contains(p)&&!blocked.contains(p);}
        public void addScaffold(Cell p){if(scaffold(p))return;assertEquals(Type.AIR,at(p));assertTrue(at(p.move(0,-1,0))==Type.SUPPORT||scaffold(p.move(0,-1,0)));scaffolds.add(p);placed.put(p,new State("minecraft:scaffolding",new CompoundTag(),null,new CompoundTag()));}
        public boolean feature(ThornbornLayout.Feature f){checks++;var s=placed.get(f.pos());return !blocked.contains(f.pos())&&s!=null&&s.id().equals(f.block())&&ThornbornLayout.stableData(s.id(),s.data()).equals(f.data());}
        void finish(ThornbornRoute route){for(int i=0;i<200000;i++)if(route.step())return;fail("Thornborn route budget exceeded");}
        void survey(int exits){finish(new ThornbornRoute(layout,reserved,exits,this));}
    }
    @Test void nativeTowerGraphsPreserveAllSourceTemplatesAndOriginalContents() throws Exception {
        var seen=new TreeSet<String>();var rows=new ArrayList<Map<String,Object>>();for(var surface:List.of(IslandPlan.Surface.GRASS,IslandPlan.Surface.ROCK))for(var r:Rotation.values())for(int v=0;v<16;v++){
            var parts=assemble(r,v);var m=new Model(parts,surface);var original=new HashMap<>(m.placed);for(int exits=1;exits<=4;exits++)m.survey(exits);
            assertFalse(m.scaffolds.isEmpty());assertEquals(parts.stream().mapToInt(p->p.bounds().minY()).min().orElseThrow(),m.layout.groundY());
            for(var e:original.entrySet())if(!m.scaffolds.contains(e.getKey()))assertEquals(e.getValue(),m.placed.get(e.getKey()));
            for(var c:m.scaffolds)assertTrue(!original.containsKey(c)||original.get(c).id().equals("minecraft:air"));
            parts.forEach(p->seen.add(p.template().id()));rows.add(Map.of("rotation",r,"variant",v,"surface",surface,"pieces",parts.size(),"groundY",m.layout.groundY(),"contentChecks",m.layout.features().size(),"scaffolds",m.scaffolds.size(),"exitCounts",List.of(1,2,3,4)));
        }
        assertEquals(source().keySet(),seen);var report=new LinkedHashMap<String,Object>();report.put("jarSha256",HASH);report.put("structure",ThornbornLayout.TARGET);report.put("cases",rows);report.put("sourceTemplates",seen);report.put("existingAlias","hanging_bridge_2_medium_terminator uses original hanging_bridge_3_medium_terminator");report.put("nativeGeneratorExecuted",false);report.put("runtimeTested",false);report.put("scope","128 original-template graphs, two main towers and selected hanging branches/embedded rooms. All 34 original resources, existing alias retained, each 1..4 exits. Native random generation, complete internal traversal, real jumping/scaffolds, combat, performance and multiplayer restart untested.");Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/thornborn-entry-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    @Test void bothNativeBridgeEndKindsProvideAccessAndRetainTheCentralFlowerpot() throws Exception {
        for(var r:Rotation.values())for(boolean broken:List.of(false,true)){var m=new Model(assemble(r,0),IslandPlan.Surface.ROCK);for(var l:m.layout.landings())if(l.broken()!=broken)m.blocked.add(l.broken()?l.at(9,12,8):l.at(0,2,0));m.survey(4);assertTrue(m.layout.landings().stream().filter(l->l.broken()==broken).anyMatch(l->m.scaffolds.contains(new Cell(l.column().x(),m.layout.groundY(),l.column().z()))));}
    }
    @Test void detachedMissingDuplicateAndIncorrectNativeFramesAreRejected() throws Exception {
        var p=assemble(Rotation.NONE,0);for(String key:List.of("part_1_support","roof_0")){var bad=new ArrayList<>(p);bad.removeIf(q->q.template().id().equals(key));assertThrows(IllegalArgumentException.class,()->inspect(bad));}
        for(int i:List.of(0,1,p.size()-1)){var bad=new ArrayList<>(p);var q=bad.get(i);bad.set(i,new Placed(q.template(),q.origin().offset(1,1,0),q.rotation()));assertThrows(IllegalArgumentException.class,()->inspect(bad));}
        var duplicate=new ArrayList<>(p);duplicate.add(p.get(1));assertThrows(IllegalArgumentException.class,()->inspect(duplicate));
        var j=sockets(p);var old=j.stream().filter(s->s.owner()==0&&s.front()==Direction.UP).findFirst().orElseThrow();j.set(j.indexOf(old),new ThornbornLayout.Socket(old.owner(),old.pos(),old.front(),old.top().getOpposite(),old.name(),old.target(),old.pool(),true));assertThrows(IllegalArgumentException.class,()->ThornbornLayout.inspect(p.stream().map(Placed::piece).toList(),ENV,j,features(p)));
    }
    @Test void blockedOriginalPathsExitsAndLateObstaclesCannotBeOverwritten() throws Exception {
        var parts=assemble(Rotation.NONE,0);var all=new Model(parts,IslandPlan.Surface.ROCK);for(var l:all.layout.landings())all.blocked.add(l.broken()?l.at(9,12,8):l.at(0,2,0));assertThrows(IllegalStateException.class,()->all.survey(1));assertTrue(all.scaffolds.isEmpty());
        var exit=new Model(parts,IslandPlan.Surface.ROCK);exit.blocked.add(exit.reserved.perimeterSites(4).get(3));assertThrows(IllegalStateException.class,()->exit.survey(4));assertTrue(exit.scaffolds.isEmpty());
        var late=new Model(parts,IslandPlan.Surface.ROCK);var route=new ThornbornRoute(late.layout,late.reserved,1,late);while(late.scaffolds.isEmpty())assertFalse(route.step());var c=late.scaffolds.iterator().next();late.blocked.add(c.move(0,1,0));assertThrows(IllegalStateException.class,()->late.finish(route));assertEquals(1,late.scaffolds.size());
    }
    @Test void originalLootAndEverySpawnerSettingAreCheckedBeforeConstruction() throws Exception {
        var m=new Model(assemble(Rotation.NONE,3),IslandPlan.Surface.GRASS);var loot=m.layout.features().stream().filter(f->!f.block().equals("minecraft:spawner")).findFirst().orElseThrow();var old=m.placed.get(loot.pos());var n=old.data().copy();n.putString("LootTable","minecraft:empty");m.placed.put(loot.pos(),new State(old.id(),old.props(),old.facing(),n));assertThrows(IllegalStateException.class,()->m.survey(1));m.placed.put(loot.pos(),old);
        var f=m.layout.features().stream().filter(a->a.block().equals("minecraft:spawner")).findFirst().orElseThrow();var s=m.placed.get(f.pos());for(var field:List.of("SpawnData","SpawnPotentials","MinSpawnDelay","MaxSpawnDelay","SpawnCount","MaxNearbyEntities","RequiredPlayerRange","SpawnRange")){var bad=s.data().copy();if(field.equals("SpawnData")||field.equals("SpawnPotentials"))bad.remove(field);else bad.putInt(field,bad.getInt(field)+1);m.placed.put(f.pos(),new State(s.id(),s.props(),s.facing(),bad));assertThrows(IllegalStateException.class,()->m.survey(1));assertTrue(m.scaffolds.isEmpty());}
        var allowed=s.data().copy();allowed.putShort("Delay",(short)79);m.placed.put(f.pos(),new State(s.id(),s.props(),s.facing(),allowed));var seed=old.data().copy();seed.putLong("LootTableSeed",789);m.placed.put(loot.pos(),new State(old.id(),old.props(),old.facing(),seed));m.survey(1);
    }
    @Test void absoluteRootEnvironmentAndRelocationRemainIndependentOfNominalTerrain() throws Exception {
        var p=assemble(Rotation.CLOCKWISE_90,1);var l=inspect(p);var moved=l.move(3000,25,-4000);assertEquals(l.groundY()+25,moved.groundY());assertEquals(l.landings().get(0).column().move(3000,25,-4000),moved.landings().get(0).column());for(int i=0;i<l.features().size();i++){assertEquals(l.features().get(i).pos().move(3000,25,-4000),moved.features().get(i).pos());assertEquals(l.features().get(i).data(),moved.features().get(i).data());}
        assertEquals(l,ThornbornLayout.inspect(p.stream().map(Placed::piece).toList(),new WdaStructurePlanner.Environment(70,8,6,16,IslandPlan.Surface.GRASS),sockets(p),features(p)));
        assertThrows(IllegalArgumentException.class,()->ThornbornLayout.inspect(p.stream().map(Placed::piece).toList(),new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.WATER,2),sockets(p),features(p)));
        var m=new Model(p,IslandPlan.Surface.ROCK);assertThrows(IllegalStateException.class,()->new ThornbornRoute(l,new IslandPlan(m.reserved.building(),l.groundY()+1,8,6,16,IslandPlan.Surface.ROCK),1,m));
    }
    @Test void pinnedSourceRetainsAbsoluteHeightAndExistingAliasWithoutAddingPoolEntries() throws Exception {
        assertEquals(HASH,HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
        try(var z=new ZipFile(JAR.toFile())){var d=json(z,"data/dungeons_arise/worldgen/structure/thornborn_towers.json");assertEquals(130,d.getAsJsonObject("start_height").get("absolute").getAsInt());assertFalse(d.has("project_start_to_heightmap"));assertFalse(d.has("terrain_adaptation"));assertEquals(7,d.get("size").getAsInt());
            for(String pool:List.of("main","hanging","rooms","spawners","terminators"))for(var raw:json(z,"data/dungeons_arise/worldgen/template_pool/fortified/thornborn_towers/thornborn_towers_"+pool+".json").getAsJsonArray("elements")){var e=raw.getAsJsonObject().getAsJsonObject("element");String k=e.get("location").getAsString().substring(ThornbornLayout.PREFIX.length());assertArrayEquals(ThornbornLayout.size(k),new int[]{source().get(k).size().getX(),source().get(k).size().getY(),source().get(k).size().getZ()});assertEquals("rigid",e.get("projection").getAsString());assertEquals("minecraft:empty",e.get("processors").getAsString());}}
    }
}
