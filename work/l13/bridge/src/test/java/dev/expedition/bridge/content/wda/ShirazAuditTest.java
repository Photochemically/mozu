package dev.expedition.bridge.content.wda;

import com.google.gson.GsonBuilder;
import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

/** Compact palette arrays avoid retaining two million heavyweight block records per palace sample. */
class ShirazAuditTest {
    static final int Y=96;
    static final WdaStructurePlanner.Environment ENV=new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.ROCK);
    record State(String id,CompoundTag props,CompoundTag data) {}
    record Joint(BlockPos pos,Direction front,Direction top,CompoundTag n) {}
    record LocalFeature(BlockPos pos,State state) {}
    record Template(String key,BlockPos size,int[] cells,State[] palette,List<Joint> joints,List<LocalFeature> features) {
        State at(BlockPos p){int i=cells[p.getX()+size.getX()*(p.getZ()+size.getZ()*p.getY())];return i<0?null:palette[i];}
    }
    record Placed(Template t,BlockPos origin,Rotation r) {
        BlockPos at(BlockPos p){return p.rotate(r).offset(origin);}
        WdaTemplatePiece piece(){var end=at(t.size().offset(-1,-1,-1));return new WdaTemplatePiece(ShirazLayout.PREFIX+t.key(),origin,r,
                new BlockBounds(Math.min(origin.getX(),end.getX()),origin.getY(),Math.min(origin.getZ(),end.getZ()),Math.max(origin.getX(),end.getX()),end.getY(),Math.max(origin.getZ(),end.getZ())),false);}
    }
    static Map<String,Template> cache;
    static Map<String,Template> templates() throws Exception {
        if(cache!=null)return cache;var result=new HashMap<String,Template>();
        try(var zip=new ZipFile(WdaTemplateFixture.JAR.toFile())) {
            for(var entry:Collections.list(zip.entries()))if(entry.getName().startsWith("data/dungeons_arise/structures/shiraz_palace/")&&entry.getName().endsWith(".nbt")) {
                CompoundTag n;try(var input=zip.getInputStream(entry)){n=NbtIo.readCompressed(input);}
                assertTrue(n.getList("entities",10).isEmpty());var size=n.getList("size",3);var dim=new BlockPos(size.getInt(0),size.getInt(1),size.getInt(2));
                var pal=n.getList("palette",10);var palette=new State[pal.size()+32];var finals=new HashMap<String,Integer>();for(int i=0;i<pal.size();i++){var s=pal.getCompound(i);palette[i]=new State(s.getString("Name"),s.getCompound("Properties"),new CompoundTag());}
                var cells=new int[dim.getX()*dim.getY()*dim.getZ()];Arrays.fill(cells,-1);var joints=new ArrayList<Joint>();var features=new ArrayList<LocalFeature>();
                for(var raw:n.getList("blocks",10)){var b=(CompoundTag)raw;var v=b.getList("pos",3);var p=new BlockPos(v.getInt(0),v.getInt(1),v.getInt(2));int state=b.getInt("state");var s=palette[state];
                    if(s.id().equals("minecraft:jigsaw")){var d=s.props().getString("orientation").split("_");joints.add(new Joint(p,Direction.byName(d[0]),Direction.byName(d[1]),b.getCompound("nbt")));
                        String id=b.getCompound("nbt").getString("final_state");if(!id.equals("minecraft:structure_void")){int slot=finals.computeIfAbsent(id,k->pal.size()+finals.size());palette[slot]=new State(id,new CompoundTag(),new CompoundTag());cells[p.getX()+dim.getX()*(p.getZ()+dim.getZ()*p.getY())]=slot;}continue;}
                    if(s.id().equals("minecraft:structure_void")||s.id().equals("minecraft:structure_block"))continue;
                    cells[p.getX()+dim.getX()*(p.getZ()+dim.getZ()*p.getY())]=state;
                    if(Set.of("minecraft:chest","minecraft:barrel","minecraft:spawner","minecraft:water","minecraft:lava").contains(s.id())){
                        var data=b.getCompound("nbt");if(s.id().equals("minecraft:water")||s.id().equals("minecraft:lava")){data=new CompoundTag();data.putInt("level",Integer.parseInt(s.props().getString("level")));}
                        features.add(new LocalFeature(p,new State(s.id(),s.props(),data)));
                    }
                }
                String k=entry.getName().substring(entry.getName().lastIndexOf('/')+1).replace("shiraz_palace_","").replace(".nbt","");
                result.put(k,new Template(k,dim,cells,palette,joints,features));
            }
        }
        assertEquals(42,result.size());cache=Map.copyOf(result);return cache;
    }
    static Placed connect(Placed parent,Joint out,Template child) {
        var in=child.joints().stream().filter(j->j.n().getString("name").equals(out.n().getString("target"))).findFirst().orElseThrow();
        var front=parent.r().rotate(out.front());var top=parent.r().rotate(out.top());
        var r=Arrays.stream(Rotation.values()).filter(v->v.rotate(in.front())==front.getOpposite()&&(!out.n().getString("joint").equals("aligned")||v.rotate(in.top())==top)).findFirst().orElseThrow();
        return new Placed(child,parent.at(out.pos()).relative(front).subtract(in.pos().rotate(r)),r);
    }
    static List<Placed> assemble(Rotation r,int variant) throws Exception {
        var t=templates();var root=new Placed(t.get("part_13"),new BlockPos(-113,Y+15,221),r);var parts=new ArrayList<>(List.of(root));var known=new HashMap<String,Placed>();known.put(root.t().key(),root);
        for(int i=0;i<parts.size();i++)for(var out:parts.get(i).t().joints())if(out.n().getString("pool").equals(ShirazLayout.POOL+"main")) {
            var child=t.values().stream().filter(v->v.key().startsWith("part_")&&v.joints().stream().anyMatch(j->j.n().getString("name").equals(out.n().getString("target")))).findFirst().orElseThrow();
            var p=connect(parts.get(i),out,child);var old=known.putIfAbsent(child.key(),p);if(old==null)parts.add(p);else assertEquals(old.piece(),p.piece());
        }
        assertEquals(27,parts.size());int i=variant;
        for(var p:List.copyOf(parts))for(var out:p.t().joints())if(out.n().getString("pool").equals(ShirazLayout.POOL+"spawners")) {
            var candidates=t.values().stream().filter(v->ShirazLayout.ENCOUNTERS.contains(v.key())&&v.joints().stream().anyMatch(j->j.n().getString("name").equals(out.n().getString("target")))).sorted(Comparator.comparing(Template::key)).toList();
            parts.add(connect(p,out,candidates.get(i++%candidates.size())));
        }
        return parts;
    }
    static List<ShirazLayout.Socket> sockets(List<Placed> p){var result=new ArrayList<ShirazLayout.Socket>();for(int i=0;i<p.size();i++){var a=p.get(i);for(var j:a.t().joints())result.add(new ShirazLayout.Socket(i,a.at(j.pos()),a.r().rotate(j.front()),a.r().rotate(j.top()),j.n().getString("name"),j.n().getString("target"),j.n().getString("pool"),j.n().getString("joint").equals("aligned")));}return result;}
    static List<ShirazLayout.Feature> features(List<Placed> p){var result=new ArrayList<ShirazLayout.Feature>();for(var a:p)for(var f:a.t().features()){var at=a.at(f.pos());result.add(new ShirazLayout.Feature(new Cell(at.getX(),at.getY(),at.getZ()),f.state().id(),ShirazLayout.stableData(f.state().id(),f.state().data())));}return result;}
    static ShirazLayout inspect(List<Placed> p){return ShirazLayout.inspect(p.stream().map(Placed::piece).toList(),ENV,sockets(p),features(p));}
    static Rotation inverse(Rotation r){return switch(r){case NONE->r;case CLOCKWISE_90->Rotation.COUNTERCLOCKWISE_90;case CLOCKWISE_180->r;case COUNTERCLOCKWISE_90->Rotation.CLOCKWISE_90;};}
    static class Model implements ShirazRoute.Probe {
        final ShirazLayout layout;final IslandPlan island;final Map<Integer,Template> shell=new HashMap<>();
        final Map<Cell,State> special=new HashMap<>(),overrides=new HashMap<>();final Map<Cell,Integer> scaffolds=new HashMap<>();final Set<Cell> blocked=new HashSet<>();int checks,writes;
        Model(List<Placed> parts,IslandPlan.Surface surface){layout=inspect(parts);var b=parts.stream().map(p->p.piece().bounds()).reduce(IslandPlan::union).orElseThrow();island=new IslandPlan(b,layout.groundY(),8,6,16,surface);
            for(var p:parts){if(p.t().key().startsWith("part_"))shell.put(Integer.parseInt(p.t().key().substring(5)),p.t());for(var f:p.t().features()){var q=p.at(f.pos());special.put(new Cell(q.getX(),q.getY(),q.getZ()),f.state());}}
        }
        State state(Cell p){if(overrides.containsKey(p))return overrides.get(p);if(special.containsKey(p))return special.get(p);var q=new BlockPos(p.x(),p.y(),p.z()).subtract(layout.origin()).rotate(inverse(layout.rotation()));
            if(q.getX()<0||q.getX()>=144||q.getZ()<0||q.getZ()>=144||q.getY()<0||q.getY()>=96)return null;
            int[][] indices={{0,1,2},{5,4,3},{6,7,8}};var t=shell.get(q.getY()/32*9+indices[q.getZ()/48][q.getX()/48]);return t.at(new BlockPos(q.getX()%48,q.getY()%32,q.getZ()%48));
        }
        public Type at(Cell p){if(blocked.contains(p)||scaffolds.containsKey(p))return Type.BLOCKED;var s=state(p);if(s==null)return p.y()<island.firstFreeY()?Type.SUPPORT:Type.AIR;
            return InteriorBlockRules.classify(s.id(),s.props().getString("type"),s.props().getString("half"),s.props().getString("waterlogged").equals("true"),!s.data().isEmpty());}
        public boolean stair(Cell p,Direction facing,String shape){var s=state(p);return !blocked.contains(p)&&s!=null&&s.id().equals("minecraft:smooth_sandstone_stairs")&&layout.rotation().rotate(Direction.byName(s.props().getString("facing")))==facing
                &&s.props().getString("half").equals("bottom")&&s.props().getString("shape").equals(shape)&&s.props().getString("waterlogged").equals("true");}
        public boolean feature(ShirazLayout.Feature f){checks++;var s=state(f.pos());return !blocked.contains(f.pos())&&s!=null&&s.id().equals(f.block())&&ShirazLayout.stableData(s.id(),s.data()).equals(f.data());}
        public boolean scaffold(Cell p,int d){return !blocked.contains(p)&&Objects.equals(scaffolds.get(p),d);}
        public void addScaffold(Cell p,int d){if(scaffold(p,d))return;assertEquals(Type.AIR,at(p));int actual=at(p.move(0,-1,0))==Type.SUPPORT?0:7;
            for(var v:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})actual=Math.min(actual,scaffolds.getOrDefault(p.move(v[0],0,v[1]),7)+1);
            assertEquals(d,actual,"Unsupported bridge construction order");scaffolds.put(p,d);writes++;
        }
        void finish(ShirazRoute route){for(int i=0;i<60000;i++)if(route.step())return;fail("Palace route exceeded budget");}
        void survey(int exits){finish(new ShirazRoute(layout,island,exits,this));}
    }
    @Test void completePalaceShellAndNativeEncountersPreserveGardenAccess() throws Exception {
        var rows=new ArrayList<Map<String,Object>>();var seen=new TreeSet<String>();
        for(var r:Rotation.values())for(var surface:List.of(IslandPlan.Surface.GRASS,IslandPlan.Surface.ROCK))for(int variant=0;variant<3;variant++) {
            var parts=assemble(r,variant);var m=new Model(parts,surface);for(int exits=1;exits<=4;exits++)m.survey(exits);
            assertEquals(88,parts.size());assertEquals(9,m.writes);assertTrue(m.overrides.isEmpty());assertEquals(2286,m.layout.features().size());assertEquals(Y+16,m.island.firstFreeY());
            assertEquals(144,m.island.building().width());assertEquals(96,m.island.building().height());assertEquals(144,m.island.building().depth());assertEquals(m.layout.features().size()*4,m.checks);
            for(var p:parts)seen.add(p.t().key());rows.add(Map.of("rotation",r,"surface",surface,"variant",variant,"pieces",88,"sourceContentChecks",2286,"scaffolds",9,"groundY",m.island.firstFreeY(),"exitCounts",List.of(1,2,3,4)));
        }
        var expected=new TreeSet<>(templates().keySet());expected.remove("husk_elite_0");assertEquals(expected,seen);
        var report=new LinkedHashMap<String,Object>();report.put("jarSha256",WdaTemplateFixture.HASH);report.put("structure",ShirazLayout.TARGET);report.put("cases",rows);report.put("sourceTemplates",seen);report.put("unusedSourceTemplates",List.of("husk_elite_0"));
        report.put("nativeGeneratorExecuted",false);report.put("runtimeTested",false);report.put("scope","24 complete original-template graphs: 27 shell cubes, 61 original encounters, all 41 reachable templates (one of 42 files not enrolled upstream). Original waterlogged stairs, 128 containers, 1873 water and 224 lava blocks retained. Nine supported scaffolds connect the northern garden; 1..4 exits. Native generator, full palace traversal, collisions, fluid/fire behavior, combat and multiplayer restart untested.");
        Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/shiraz-entry-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    @Test void missingMisplacedAndDuplicateShellOrEncountersReject() throws Exception {
        var p=assemble(Rotation.CLOCKWISE_90,0);
        for(int index:new int[]{0,1,20,27,70}){var bad=new ArrayList<>(p);bad.remove(index);assertThrows(IllegalArgumentException.class,()->inspect(bad));}
        var duplicate=new ArrayList<>(p);duplicate.add(p.get(1));assertThrows(IllegalArgumentException.class,()->inspect(duplicate));
        for(int index:new int[]{1,27}){var bad=new ArrayList<>(p);var old=bad.get(index);bad.set(index,new Placed(old.t(),old.origin().offset(1,0,0),old.r()));assertThrows(IllegalArgumentException.class,()->inspect(bad));}
        var joints=new ArrayList<>(sockets(p));int i=0;while(joints.get(i).front()!=Direction.DOWN||!joints.get(i).pool().endsWith("spawners"))i++;var j=joints.get(i);joints.set(i,new ShirazLayout.Socket(j.owner(),j.pos(),j.front(),j.top().getOpposite(),j.name(),j.target(),j.pool(),false));
        assertDoesNotThrow(()->ShirazLayout.inspect(p.stream().map(Placed::piece).toList(),ENV,joints,features(p)));
        int main=0;while(!(joints.get(main).owner()==0&&joints.get(main).front()==Direction.UP&&joints.get(main).pool().endsWith("main")))main++;var upper=joints.get(main);
        joints.set(main,new ShirazLayout.Socket(upper.owner(),upper.pos(),upper.front(),upper.top().getOpposite(),upper.name(),upper.target(),upper.pool(),true));
        assertThrows(IllegalArgumentException.class,()->ShirazLayout.inspect(p.stream().map(Placed::piece).toList(),ENV,joints,features(p)));
    }
    @Test void bridgeRequiresBothSupportsOriginalWetStairsAndUnobstructedExit() throws Exception {
        for(var point:List.of(new int[]{71,32,-1},new int[]{71,32,7},new int[]{71,33,3},new int[]{71,35,8},new int[]{71,32,8},new int[]{71,32,9},new int[]{71,33,10})) {
            var m=new Model(assemble(Rotation.NONE,0),IslandPlan.Surface.ROCK);m.blocked.add(m.layout.at(point[0],point[1],point[2]));assertThrows(IllegalStateException.class,()->m.survey(1));assertEquals(0,m.writes);
        }
        var m=new Model(assemble(Rotation.NONE,0),IslandPlan.Surface.ROCK);m.blocked.add(m.island.perimeterSites(4).get(3));assertThrows(IllegalStateException.class,()->m.survey(4));
        var late=new Model(assemble(Rotation.NONE,0),IslandPlan.Surface.ROCK);var route=new ShirazRoute(late.layout,late.island,1,late);while(late.writes==0)assertFalse(route.step());late.blocked.add(late.layout.at(71,35,7));assertThrows(IllegalStateException.class,()->late.finish(route));
    }
    @Test void originalLootFluidsAndSpawnerSettingsAreCheckedBeforeBridgeWrites() throws Exception {
        var m=new Model(assemble(Rotation.NONE,0),IslandPlan.Surface.ROCK);
        for(var block:List.of("minecraft:chest","minecraft:barrel","minecraft:spawner","minecraft:water","minecraft:lava")) {
            var f=m.layout.features().stream().filter(v->v.block().equals(block)).findFirst().orElseThrow();var old=m.state(f.pos());var n=old.data().copy();
            if(block.endsWith("spawner"))n.getCompound("SpawnData").getCompound("entity").putString("id","minecraft:pig");
            else if(block.endsWith("water")||block.endsWith("lava"))n.putInt("level",(n.getInt("level")+1)%16);
            else if(n.contains("LootTable"))n.putString("LootTable","minecraft:chests/simple_dungeon");else {var items=new ListTag();var item=new CompoundTag();item.putString("id","minecraft:stone");items.add(item);n.put("Items",items);}
            m.overrides.put(f.pos(),new State(old.id(),old.props(),n));assertThrows(IllegalStateException.class,()->m.survey(1));assertEquals(0,m.writes);m.overrides.clear();
        }
        var f=m.layout.features().stream().filter(v->v.block().equals("minecraft:spawner")).findFirst().orElseThrow();var old=m.state(f.pos());
        for(var field:List.of("SpawnPotentials","MinSpawnDelay","MaxSpawnDelay","SpawnCount","MaxNearbyEntities","RequiredPlayerRange","SpawnRange")) {var n=old.data().copy();if(field.equals("SpawnPotentials"))n.remove(field);else n.putInt(field,n.getInt(field)+1);m.overrides.put(f.pos(),new State(old.id(),old.props(),n));assertThrows(IllegalStateException.class,()->m.survey(1));m.overrides.clear();}
        for(var v:m.layout.features())if(!Set.of("minecraft:water","minecraft:lava").contains(v.block())) {var s=m.state(v.pos());var n=s.data().copy();n.putShort("Delay",(short)144);n.putLong("LootTableSeed",987654321);m.overrides.put(v.pos(),new State(s.id(),s.props(),n));}m.survey(1);
    }
    @Test void originalHeightEnvironmentAndRelocationAreExplicit() throws Exception {
        var p=assemble(Rotation.COUNTERCLOCKWISE_90,0);var l=inspect(p);var moved=l.move(5000,22,-3000);assertEquals(l.at(71,33,7).move(5000,22,-3000),moved.at(71,33,7));
        for(int i=0;i<l.features().size();i++){assertEquals(l.features().get(i).pos().move(5000,22,-3000),moved.features().get(i).pos());assertEquals(l.features().get(i).data(),moved.features().get(i).data());}
        assertThrows(IllegalArgumentException.class,()->ShirazLayout.inspect(p.stream().map(Placed::piece).toList(),new WdaStructurePlanner.Environment(Y+1,8,6,16,IslandPlan.Surface.ROCK),sockets(p),features(p)));
        assertThrows(IllegalArgumentException.class,()->ShirazLayout.inspect(p.stream().map(Placed::piece).toList(),new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.WATER,2),sockets(p),features(p)));
        var m=new Model(p,IslandPlan.Surface.ROCK);assertThrows(IllegalStateException.class,()->new ShirazRoute(l,new IslandPlan(m.island.building(),Y,8,6,16,IslandPlan.Surface.ROCK),1,m));
    }
    @Test void pinnedDefinitionAndUnusedEliteRemainFaithfulToSource() throws Exception {
        assertEquals(WdaTemplateFixture.HASH,HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(WdaTemplateFixture.JAR))));
        try(var z=new ZipFile(WdaTemplateFixture.JAR.toFile())){var d=WdaTemplateFixture.json(z,"data/dungeons_arise/worldgen/structure/shiraz_palace.json");assertEquals(16,d.getAsJsonObject("start_height").get("absolute").getAsInt());assertEquals("WORLD_SURFACE_WG",d.get("project_start_to_heightmap").getAsString());assertFalse(d.has("terrain_adaptation"));
            var pool=WdaTemplateFixture.json(z,"data/dungeons_arise/worldgen/template_pool/desertic/shiraz_palace/shiraz_palace_spawners.json");var keys=new HashSet<String>();for(var e:pool.getAsJsonArray("elements"))keys.add(e.getAsJsonObject().getAsJsonObject("element").get("location").getAsString().substring(ShirazLayout.PREFIX.length()));assertEquals(ShirazLayout.ENCOUNTERS,keys);}
        assertTrue(templates().containsKey("husk_elite_0"));assertThrows(IllegalArgumentException.class,()->ShirazLayout.pool("husk_elite_0"));
    }
}
