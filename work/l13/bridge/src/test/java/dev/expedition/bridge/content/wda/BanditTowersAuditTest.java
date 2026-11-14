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
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import static dev.expedition.bridge.content.wda.WdaTemplateFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class BanditTowersAuditTest {
    private static final WdaStructurePlanner.Environment ENV=new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.ROCK);
    private static List<Block> joints(Template t){return t.blocks().stream().filter(b->b.id().equals("minecraft:jigsaw")).toList();}
    private static Direction front(Block b){return Direction.byName(b.props().getString("orientation").split("_")[0]);}
    private static Placed attach(Placed parent,Block socket,Template child,Direction inlet){
        assertTrue(BanditTowersLayout.accepts(socket.data().getString("pool"),child.id()));
        // connect checks pool membership; a native fallback is just as legal as the primary pool.
        var pools=new HashSet<>(child.pools());pools.add(socket.data().getString("pool"));
        return connect(parent,socket,new Template(child.id(),child.resource(),pools,child.legacy(),child.size(),child.blocks(),child.entities()),inlet);
    }
    private static Placed vertical(Placed p,Template t,Direction direction){return attach(p,p.template().joint(direction==Direction.UP?"bandit_towers_part_bottom":"bandit_towers_part_top",direction),t,direction.getOpposite());}
    private static boolean attached(Placed p,Block b,List<Placed> parts){
        var point=p.at(b.pos()).relative(p.rotation().rotate(front(b)));
        return parts.stream().filter(q->q!=p).anyMatch(q->joints(q.template()).stream().anyMatch(in->q.at(in.pos()).equals(point)
                &&q.rotation().rotate(front(in))==p.rotation().rotate(front(b)).getOpposite()));
    }
    private static boolean overlaps(Placed a,Placed b){var x=a.bounds();var y=b.bounds();return x.minX()<=y.maxX()&&x.maxX()>=y.minX()
            &&x.minY()<=y.maxY()&&x.maxY()>=y.minY()&&x.minZ()<=y.maxZ()&&x.maxZ()>=y.minZ();}
    private static List<Placed> assemble(Map<String,Template> templates,Rotation rotation,int variant,int root){
        var first=new Placed(templates.get("crossing_"+root),new BlockPos(-113,134,221),rotation);
        var parts=new ArrayList<Placed>();parts.add(first);
        var source=joints(first.template()).stream().filter(b->b.data().getString("pool").endsWith("_bridges")).findFirst().orElseThrow();
        var bridge=attach(first,source,templates.get(variant%2==0?"crossing_3_bridge":"crossing_4_bridge_mini"),Direction.WEST);parts.add(bridge);
        var end=bridge.template().joint("bandit_towers_part_bridge",Direction.EAST);
        var second=attach(bridge,end,templates.get("crossing_5"),Direction.WEST);parts.add(second);
        for(var cross:List.of(first,second)){
            var lower=vertical(cross,templates.get("part_"+(variant%5)),Direction.DOWN);parts.add(lower);
            if(variant%2==0){lower=vertical(lower,templates.get("part_"+((variant+2)%5)),Direction.DOWN);parts.add(lower);}
            parts.add(vertical(lower,templates.get("base_terminator_0"),Direction.DOWN));
            var upper=vertical(cross,templates.get("part_"+((variant+1)%5)),Direction.UP);parts.add(upper);
            parts.add(vertical(upper,templates.get("roof_terminator_0"),Direction.UP));
        }
        for(var p:List.copyOf(parts))for(var b:joints(p.template())){
            String pool=b.data().getString("pool");
            if(pool.endsWith("_bridges")&&!attached(p,b,parts))parts.add(attach(p,b,templates.get("crossing_terminator_0"),Direction.WEST));
            if(pool.endsWith("_rooms")){
                var room=attach(p,b,templates.get(variant%5==4?"small_room_terminator_0":"small_room_"+(variant%4)),variant%5==4?Direction.NORTH:Direction.SOUTH);
                // Native placement also rejects optional rooms whose bounding boxes collide.
                if(parts.stream().noneMatch(q->overlaps(q,room)))parts.add(room);
            }
        }
        for(int i=0;i<parts.size();i++)for(int j=i+1;j<parts.size();j++)assertFalse(overlaps(parts.get(i),parts.get(j)),"Original structural boxes must not overlap");
        int encounter=variant;
        for(var p:List.copyOf(parts))for(var b:joints(p.template()))if(b.data().getString("pool").endsWith("_spawners")){
            var options=templates.values().stream().filter(t->BanditTowersLayout.accepts(BanditTowersLayout.POOL+"spawners",t.id()))
                    .filter(t->joints(t).get(0).data().getString("name").equals(b.data().getString("target"))).sorted(Comparator.comparing(Template::id)).toList();
            parts.add(attach(p,b,options.get(encounter++%options.size()),Direction.DOWN));
        }
        return parts;
    }
    private static List<BanditTowersLayout.Socket> sockets(List<Placed> parts){
        var result=new ArrayList<BanditTowersLayout.Socket>();for(int i=0;i<parts.size();i++){var p=parts.get(i);for(var b:joints(p.template())){
            var n=b.data();var axes=b.props().getString("orientation").split("_");
            result.add(new BanditTowersLayout.Socket(i,p.at(b.pos()),p.rotation().rotate(Direction.byName(axes[0])),p.rotation().rotate(Direction.byName(axes[1])),
                    n.getString("name"),n.getString("target"),n.getString("pool"),n.getString("joint").equals("aligned")));
        }}return result;
    }
    private static List<BanditTowersLayout.Feature> features(List<Placed> parts){
        var result=new ArrayList<BanditTowersLayout.Feature>();for(var p:parts)for(var b:p.template().blocks())if(Set.of("minecraft:barrel","minecraft:chest","minecraft:spawner").contains(b.id())){
            var n=b.data();var pos=p.at(b.pos());result.add(new BanditTowersLayout.Feature(new Cell(pos.getX(),pos.getY(),pos.getZ()),b.id(),n.getString("LootTable"),n.getCompound("SpawnData").copy(),n.getList("SpawnPotentials",Tag.TAG_COMPOUND).copy()));
        }return result;
    }
    private static BanditTowersLayout inspect(List<Placed> parts){return BanditTowersLayout.inspect(parts.stream().map(Placed::piece).toList(),ENV,sockets(parts),features(parts));}
    private static class Model extends Terrain implements BanditTowersRoute.Probe {
        final BanditTowersLayout layout;
        final IslandPlan reserved;
        final Set<Cell> scaffolds=new HashSet<>();
        Model(List<Placed> parts,IslandPlan.Surface surface){super(parts);layout=inspect(parts);reserved=new IslandPlan(island.building(),layout.groundY(),8,6,16,surface);}
        @Override public Type at(Cell p){if(blocked.contains(p))return Type.BLOCKED;if(!placed.containsKey(p))return p.y()<layout.groundY()?Type.SUPPORT:Type.AIR;return super.at(p);}
        public boolean scaffold(Cell p){return !blocked.contains(p)&&scaffolds.contains(p);}
        public void addScaffold(Cell p){if(scaffold(p))return;assertEquals(Type.AIR,at(p));assertTrue(at(p.move(0,-1,0))==Type.SUPPORT||scaffold(p.move(0,-1,0)));scaffolds.add(p);placed.put(p,new State("minecraft:scaffolding",new CompoundTag(),null,new CompoundTag()));}
        public boolean feature(BanditTowersLayout.Feature f){var s=placed.get(f.pos());return !blocked.contains(f.pos())&&s!=null&&s.id().equals(f.block())&&s.data().getString("LootTable").equals(f.loot())
                &&s.data().getCompound("SpawnData").equals(f.spawnData())&&s.data().getList("SpawnPotentials",Tag.TAG_COMPOUND).equals(f.spawnPotentials());}
        void survey(int exits){var route=new BanditTowersRoute(layout,reserved,exits,this);for(int i=0;i<150000;i++)if(route.step())return;fail("Tower route budget exceeded");}
    }
    @Test void nativeGraphsKeepAllTemplatesAndConnectIslandToOriginalBridgeEnds() throws Exception {
        var templates=templates("bandit_towers");var seen=new TreeSet<String>();var rows=new ArrayList<Map<String,Object>>();
        for(var surface:List.of(IslandPlan.Surface.GRASS,IslandPlan.Surface.ROCK))for(var rotation:Rotation.values())for(int variant=0;variant<10;variant++){
            int root=variant%3;var parts=assemble(templates,rotation,variant,root);var m=new Model(parts,surface);
            for(int exits=1;exits<=4;exits++)m.survey(exits);
            assertFalse(m.scaffolds.isEmpty());assertEquals(parts.stream().mapToInt(p->p.bounds().minY()).min().orElseThrow(),m.layout.groundY());
            for(var p:parts){seen.add(p.template().id());assertTrue(p.template().entities().isEmpty());for(var b:p.template().blocks())if(b.data().contains("LootTable")||b.id().equals("minecraft:spawner")){
                var pos=p.at(b.pos());assertEquals(b.data(),m.placed.get(new Cell(pos.getX(),pos.getY(),pos.getZ())).data());
            }}
            rows.add(Map.of("rotation",rotation,"variant",variant,"root",root,"surface",surface,"pieces",parts.size(),"groundY",m.layout.groundY(),"scaffoldBlocks",m.scaffolds.size(),"exitCounts",List.of(1,2,3,4)));
        }
        assertEquals(templates.keySet(),seen);var report=new LinkedHashMap<String,Object>();report.put("jarSha256",HASH);report.put("structure",BanditTowersLayout.TARGET);report.put("cases",rows);report.put("sourceTemplates",seen);
        report.put("nativeGeneratorExecuted",false);report.put("runtimeTested",false);report.put("scope","80 hand-assembled two-tower original-template graphs, both bridge lengths, three roots, four rotations and two island materials. Entrance reaches original bridge; complete internal exploration and native generation acceptance rate remain runtime checks.");
        Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/bandit-towers-entry-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    @Test void missingCapsDisconnectedPiecesAndChangedHeightFailBeforeAllocation() throws Exception {
        var parts=assemble(templates("bandit_towers"),Rotation.CLOCKWISE_90,1,1);
        for(int index=1;index<parts.size();index++)if(parts.get(index).template().id().contains("terminator")&&!parts.get(index).template().id().startsWith("small_room")){
            var bad=new ArrayList<>(parts);bad.remove(index);assertThrows(IllegalArgumentException.class,()->inspect(bad));
        }
        for(int index:List.of(0,1,3,parts.size()-1)){var bad=new ArrayList<>(parts);var p=bad.get(index);bad.set(index,new Placed(p.template(),p.origin().offset(1,1,0),p.rotation()));assertThrows(IllegalArgumentException.class,()->inspect(bad));}
        var duplicate=new ArrayList<>(parts);duplicate.add(parts.get(1));assertThrows(IllegalArgumentException.class,()->inspect(duplicate));
    }
    @Test void blockedApproachesAndExitOrChangedSpawnerRefuseWithoutReplacingAnything() throws Exception {
        var parts=assemble(templates("bandit_towers"),Rotation.NONE,3,0);
        var all=new Model(parts,IslandPlan.Surface.ROCK);for(var l:all.layout.landings())all.blocked.add(l.at(0,4,2));
        assertThrows(IllegalStateException.class,()->all.survey(1));assertTrue(all.scaffolds.isEmpty());
        var exit=new Model(parts,IslandPlan.Surface.ROCK);exit.blocked.add(exit.reserved.perimeterSites(4).get(3));assertThrows(IllegalStateException.class,()->exit.survey(4));assertTrue(exit.scaffolds.isEmpty());
        var spawner=new Model(parts,IslandPlan.Surface.GRASS);var f=spawner.layout.features().stream().filter(v->v.block().equals("minecraft:spawner")).findFirst().orElseThrow();
        var state=spawner.placed.get(f.pos());var altered=state.data().copy();altered.remove("SpawnData");spawner.placed.put(f.pos(),new State(state.id(),state.props(),state.facing(),altered));
        assertThrows(IllegalStateException.class,()->spawner.survey(2));assertTrue(spawner.scaffolds.isEmpty());
    }
    @Test void relocationPreservesNativeHeightRelationshipsAndDevicePayloads() throws Exception {
        var layout=inspect(assemble(templates("bandit_towers"),Rotation.COUNTERCLOCKWISE_90,2,2));var moved=layout.move(3000,-17,-4000);
        assertEquals(layout.groundY()-17,moved.groundY());assertEquals(layout.landings().get(0).origin().offset(3000,-17,-4000),moved.landings().get(0).origin());
        for(int i=0;i<layout.features().size();i++){var a=layout.features().get(i);var b=moved.features().get(i);assertEquals(a.pos().move(3000,-17,-4000),b.pos());assertEquals(a.spawnData(),b.spawnData());assertEquals(a.loot(),b.loot());}
    }
    @Test void alternateEndWorksAndLateColumnObstructionCannotBeOverwritten() throws Exception {
        var parts=assemble(templates("bandit_towers"),Rotation.NONE,3,0);
        var alternate=new Model(parts,IslandPlan.Surface.ROCK);alternate.blocked.add(alternate.layout.landings().get(0).at(0,4,2));alternate.survey(4);
        var late=new Model(parts,IslandPlan.Surface.ROCK);var route=new BanditTowersRoute(late.layout,late.reserved,1,late);
        for(int i=0;late.scaffolds.isEmpty()&&i<150000;i++)assertFalse(route.step());assertFalse(late.scaffolds.isEmpty());
        var first=late.scaffolds.iterator().next();var obstacle=first.move(0,1,0);late.blocked.add(obstacle);
        int count=late.scaffolds.size();assertThrows(IllegalStateException.class,route::step);assertEquals(count,late.scaffolds.size());assertFalse(late.scaffolds.contains(obstacle));
    }
    @Test void lockedNativeDefinitionsRetainAbsoluteHeightFallbacksAndEmptyRoomChoice() throws Exception {
        assertEquals(HASH,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
        try(var zip=new ZipFile(JAR.toFile())){
            var definition=json(zip,"data/dungeons_arise/worldgen/structure/bandit_towers.json");assertEquals(135,definition.getAsJsonObject("start_height").get("absolute").getAsInt());assertFalse(definition.has("project_start_to_heightmap"));assertEquals(7,definition.get("size").getAsInt());
            for(String pool:List.of("main","crossing","bridges","rooms"))assertEquals(BanditTowersLayout.POOL+"terminators",json(zip,"data/dungeons_arise/worldgen/template_pool/bandit/bandit_towers/bandit_towers_"+pool+".json").get("fallback").getAsString());
            var rooms=json(zip,"data/dungeons_arise/worldgen/template_pool/bandit/bandit_towers/bandit_towers_rooms.json").getAsJsonArray("elements");assertTrue(rooms.asList().stream().anyMatch(e->e.getAsJsonObject().getAsJsonObject("element").get("element_type").getAsString().equals("minecraft:empty_pool_element")));
        }
    }
}
