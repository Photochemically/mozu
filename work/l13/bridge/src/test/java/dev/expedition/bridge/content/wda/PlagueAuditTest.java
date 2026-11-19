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

class PlagueAuditTest {
    private static final WdaStructurePlanner.Environment ENV=new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.ROCK,0,false,DungeonSpec.EntryMode.START_ROOM);
    private static List<Block> joints(Placed p){return p.template().blocks().stream().filter(b->b.id().equals("minecraft:jigsaw")).toList();}
    private static Direction front(Block b){return Direction.byName(b.props().getString("orientation").split("_")[0]);}
    private static Placed attach(Placed parent,Block socket,Template child) {
        var inlet=child.blocks().stream().filter(b->b.id().equals("minecraft:jigsaw")&&b.data().getString("name").equals(socket.data().getString("target"))).findFirst().orElseThrow();
        return connect(parent,socket,child,front(inlet));
    }
    private static Block socket(Placed p,String target,Direction worldDirection) {
        return joints(p).stream().filter(b->b.data().getString("target").equals("dungeons_arise:"+target)&&p.rotation().rotate(front(b))==worldDirection)
                .max(Comparator.comparingInt(b->b.pos().getY())).orElseThrow();
    }
    private static List<Placed> assemble(Map<String,Template> t,Rotation rotation,int variant) {
        var root=new Placed(t.get(PlagueLayout.ROOT),new BlockPos(-113,-1,221),rotation);
        var result=new ArrayList<>(List.of(root));
        var north=rotation.rotate(Direction.NORTH);var south=north.getOpposite();var east=rotation.rotate(Direction.EAST);
        String[] roomKeys={"room_0","room_1_double","room_2_double","room_4_pit","room_5_brews","room_6_water"};
        var room=attach(root,socket(root,"plague_asylum_room",north),t.get(roomKeys[variant%roomKeys.length]));result.add(room);
        var cells=attach(room,socket(room,"plague_asylum_corridor",north),t.get("corridor_1_cells"));result.add(cells);
        var normal=joints(cells).stream().filter(b->b.data().getString("target").endsWith("cell_normal")).findFirst().orElseThrow();
        result.add(attach(cells,normal,t.get("cell_normal_"+(variant%7))));
        String[] corridors={"corridor_0","corridor_1_cells","corridor_2_crossing","corridor_3_stairway",PlagueLayout.ROOT};
        result.add(attach(root,socket(root,"plague_asylum_corridor",south),t.get(corridors[variant%5])));
        var last=root;
        for(int i=0;i<3;i++){last=attach(last,socket(last,"plague_asylum_passage",east),t.get("passage_"+((variant+i)%3)));result.add(last);}
        var secured=attach(last,socket(last,"plague_asylum_passage",east),t.get("room_3_cells"));result.add(secured);
        int cell=variant;
        for(var j:joints(secured))if(j.data().getString("target").endsWith("cell_security"))result.add(attach(secured,j,t.get("cell_security_"+(cell++%5))));
        int spawn=variant;
        for(var p:List.copyOf(result))for(var j:joints(p))if(front(j)==Direction.UP&&j.data().getString("pool").equals(PlagueLayout.POOL+"spawners")) {
            String target=j.data().getString("target"), key=target.endsWith("plague_doctor")?new String[]{"skeleton_0","skeleton_1","skeleton_2_wither"}[spawn++%3]
                    :target.substring(target.indexOf(':')+1)+"_0";
            result.add(attach(p,j,t.get(key)));
        }
        // External rooms cannot overlap. Native internal decorations/spawners may occupy their parent's box.
        for(int i=0;i<result.size();i++)for(int j=i+1;j<result.size();j++) {
            var a=result.get(i);var b=result.get(j);
            if(a.template().id().startsWith("cell_")||b.template().id().startsWith("cell_")||a.template().size().getX()==1||b.template().size().getX()==1)continue;
            assertFalse(a.bounds().intersects(b.bounds()),"Overlapping fixture components: "+a.template().id()+" / "+b.template().id());
        }
        return result;
    }
    private static List<PlagueLayout.Socket> sockets(List<Placed> parts) {
        var result=new ArrayList<PlagueLayout.Socket>();
        for(int i=0;i<parts.size();i++){var p=parts.get(i);for(var b:joints(p)) {
            var n=b.data();var d=b.props().getString("orientation").split("_");
            result.add(new PlagueLayout.Socket(i,p.at(b.pos()),p.rotation().rotate(Direction.byName(d[0])),p.rotation().rotate(Direction.byName(d[1])),n.getString("name"),n.getString("target"),n.getString("pool"),n.getString("joint").equals("aligned"),n.getString("final_state").equals("minecraft:air")));
        }}return result;
    }
    private static List<PlagueLayout.Feature> features(List<Placed> parts) {
        var result=new ArrayList<PlagueLayout.Feature>();
        for(int owner=0;owner<parts.size();owner++){var p=parts.get(owner);for(var b:p.template().blocks())if(Set.of("minecraft:chest","minecraft:barrel","minecraft:dispenser","minecraft:spawner","minecraft:water").contains(b.id())) {
            var n=b.data();if(b.id().equals("minecraft:water")){n=new CompoundTag();n.putInt("level",Integer.parseInt(b.props().getString("level")));}
            var pos=p.at(b.pos());result.add(new PlagueLayout.Feature(owner,new Cell(pos.getX(),pos.getY(),pos.getZ()),b.id(),PlagueLayout.stableData(b.id(),n)));
        }}return result;
    }
    private static BlockBounds complete(List<Placed> parts){return parts.stream().map(Placed::bounds).reduce(IslandPlan::union).orElseThrow().expand(12);}
    private static PlagueLayout inspect(List<Placed> parts){return PlagueLayout.inspect(parts.stream().map(Placed::piece).toList(),ENV,complete(parts),sockets(parts),features(parts));}
    private static class Model extends Terrain {
        final PlagueLayout layout;
        final IslandPlan reserved;
        int checked;
        Model(List<Placed> parts){super(parts);layout=inspect(parts);reserved=new IslandPlan(complete(parts),Y,8,6,16,IslandPlan.Surface.ROCK);InteriorRoom.requireRockCover(reserved);}
        boolean feature(PlagueLayout.Feature f){checked++;var s=placed.get(f.pos());if(blocked.contains(f.pos())||s==null||!s.id().equals(f.block()))return false;
            var n=s.data();if(s.id().equals("minecraft:water")){n=new CompoundTag();n.putInt("level",Integer.parseInt(s.props().getString("level")));}
            return PlagueLayout.stableData(s.id(),n).equals(f.data());
        }
        void audit(){var check=new PlagueContentCheck(layout,this::feature);for(int i=0;i<=layout.features().size();i++)if(check.step())return;fail("Unbounded content check");}
        List<PortalRecord.Site> exits(int count){var job=new WdaInteriorSitesJob(layout.rooms(),count,this::at,"dimdoors:dungeon");for(int i=0;i<10000;i++)if(job.step(128,100_000_000))return job.sites();throw new AssertionError("Unbounded interior search");}
    }
    @Test void originalUndergroundGraphsHaveStartAndDistributedExitsWithoutCarving() throws Exception {
        var templates=templates("plague_asylum");var rows=new ArrayList<Map<String,Object>>();var seen=new TreeSet<String>();
        for(var r:Rotation.values())for(int variant=0;variant<14;variant++) {
            var parts=assemble(templates,r,variant);var m=new Model(parts);var before=new HashMap<>(m.placed);m.audit();
            for(int n=1;n<=4;n++) {
                var sites=m.exits(n);assertEquals(n,sites.size());
                var first=sites.get(0);assertTrue(parts.get(0).bounds().contains(first.x(),first.y(),first.z()));
                for(var s:sites)assertTrue(InteriorSiteSearch.clearPlatform(new Cell(s.x(),s.y(),s.z()),m::at));
                for(int i=0;i<n;i++)for(int j=i+1;j<n;j++)assertTrue(InteriorSiteSearch.separated(new Cell(sites.get(i).x(),sites.get(i).y(),sites.get(i).z()),new Cell(sites.get(j).x(),sites.get(j).y(),sites.get(j).z())));
            }
            assertEquals(before,m.placed);assertEquals(m.layout.features().size(),m.checked);
            for(var p:parts)seen.add(p.template().id());
            rows.add(Map.of("rotation",r,"variant",variant,"pieces",parts.size(),"roomsWithPassages",m.layout.rooms().size(),"checkedFeatures",m.layout.features().size(),"completeBounds",m.reserved.building(),"exitCounts",List.of(1,2,3,4)));
        }
        assertEquals(templates.keySet(),seen);
        var report=new LinkedHashMap<String,Object>();report.put("jarSha256",HASH);report.put("structure",PlagueLayout.TARGET);report.put("cases",rows);report.put("sourceTemplates",seen);
        report.put("runtimeTested",false);report.put("nativeGeneratorExecuted",false);report.put("scope","56 original-template graphs, all 33 resources, four rotations and 14 branch/decor cycles. Native-compatible selected branches, rock cover, start-room and 1..4 distinct-room exit selection. No route writes. Not an exhaustive native random generator or full dungeon traversal test.");
        Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/plague-entry-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    @Test void disconnectedDuplicateMisalignedAndUnreservedPiecesReject() throws Exception {
        var parts=assemble(templates("plague_asylum"),Rotation.CLOCKWISE_90,3);
        var duplicate=new ArrayList<>(parts);duplicate.add(parts.get(1));assertThrows(IllegalArgumentException.class,()->inspect(duplicate));
        var missing=new ArrayList<>(parts);missing.remove(1);assertThrows(IllegalArgumentException.class,()->inspect(missing));
        var moved=new ArrayList<>(parts);var p=moved.get(1);moved.set(1,new Placed(p.template(),p.origin().offset(1,0,0),p.rotation()));assertThrows(IllegalArgumentException.class,()->inspect(moved));
        var joints=new ArrayList<>(sockets(parts));int i=0;while(joints.get(i).front()!=Direction.DOWN)i++;var j=joints.get(i);
        joints.set(i,new PlagueLayout.Socket(j.owner(),j.pos(),j.front(),j.top().getOpposite(),j.name(),j.target(),j.pool(),true,j.air()));
        assertThrows(IllegalArgumentException.class,()->PlagueLayout.inspect(parts.stream().map(Placed::piece).toList(),ENV,complete(parts),joints,features(parts)));
        assertThrows(IllegalArgumentException.class,()->PlagueLayout.inspect(parts.stream().map(Placed::piece).toList(),ENV,complete(parts).expand(1),sockets(parts),features(parts)));
    }
    @Test void nativeEmptyBranchesStayOptionalAndRootCannotBeReplaced() throws Exception {
        var root=new Placed(templates("plague_asylum").get(PlagueLayout.ROOT),new BlockPos(0,-1,0),Rotation.NONE);
        var m=new Model(List.of(root));assertEquals(1,m.exits(1).size());assertThrows(IllegalStateException.class,()->m.exits(2));
        assertThrows(IllegalArgumentException.class,()->inspect(List.of(new Placed(root.template(),root.origin().above(),root.rotation()))));
        var partial=new ArrayList<>(assemble(templates("plague_asylum"),Rotation.NONE,0));partial.remove(0);assertThrows(IllegalArgumentException.class,()->inspect(partial));
    }
    @Test void originalLootTrapsWaterAndSpawnerSettingsCannotBeSilentlyReplaced() throws Exception {
        var templates=templates("plague_asylum");
        for(int variant:List.of(3,5)) {
            var m=new Model(assemble(templates,Rotation.NONE,variant));
            for(var f:m.layout.features()) {
                var old=m.placed.get(f.pos());m.blocked.add(f.pos());assertThrows(IllegalStateException.class,m::audit);m.blocked.remove(f.pos());
                var n=old.data().copy();
                if(f.block().equals("minecraft:spawner"))n.getCompound("SpawnData").getCompound("entity").putString("id","minecraft:pig");
                else if(n.contains("LootTable"))n.putString("LootTable","minecraft:chests/simple_dungeon");
                else if(f.block().equals("minecraft:dispenser")) {
                    var changed=new net.minecraft.nbt.ListTag();var item=new CompoundTag();item.putByte("Slot",(byte)0);item.putString("id","minecraft:stone");item.putByte("Count",(byte)1);changed.add(item);n.put("Items",changed);
                }
                else if(f.block().equals("minecraft:water")) {
                    var properties=old.props().copy();properties.putString("level","1");
                    m.placed.put(f.pos(),new State(old.id(),properties,old.facing(),old.data()));assertThrows(IllegalStateException.class,m::audit);m.placed.put(f.pos(),old);continue;
                }
                else continue;
                m.placed.put(f.pos(),new State(old.id(),old.props(),old.facing(),n));assertThrows(IllegalStateException.class,m::audit);m.placed.put(f.pos(),old);
            }
            var f=m.layout.features().stream().filter(x->x.block().equals("minecraft:spawner")).findFirst().orElseThrow();var old=m.placed.get(f.pos());
            for(String field:List.of("SpawnPotentials","SpawnCount","MinSpawnDelay","MaxSpawnDelay","SpawnRange","MaxNearbyEntities","RequiredPlayerRange")){
                var n=old.data().copy();if(field.equals("SpawnPotentials"))n.remove(field);else n.putInt(field,n.getInt(field)+1);
                m.placed.put(f.pos(),new State(old.id(),old.props(),old.facing(),n));assertThrows(IllegalStateException.class,m::audit);
            }
            m.placed.put(f.pos(),old);m.audit();
        }
    }
    @Test void runtimeSeedsAndCountdownsAreNotMistakenForSourceConstants() throws Exception {
        var m=new Model(assemble(templates("plague_asylum"),Rotation.NONE,5));
        for(var f:m.layout.features())if(!f.block().equals("minecraft:water")) {
            var old=m.placed.get(f.pos());var n=old.data().copy();n.putLong("LootTableSeed",123456789L);n.putShort("Delay",(short)171);
            m.placed.put(f.pos(),new State(old.id(),old.props(),old.facing(),n));
        }
        m.audit();
    }
    @Test void blockedStartCannotFallBackToAnotherRoomAndRelocationPreservesEvidence() throws Exception {
        var m=new Model(assemble(templates("plague_asylum"),Rotation.COUNTERCLOCKWISE_90,0));var room=m.layout.rooms().get(0);
        var sites=m.exits(4);var shifted=m.layout.move(4000,32,-3000);
        for(int i=0;i<m.layout.rooms().size();i++)assertEquals(m.layout.rooms().get(i).move(4000,32,-3000),shifted.rooms().get(i));
        for(int i=0;i<m.layout.features().size();i++)assertEquals(m.layout.features().get(i).pos().move(4000,32,-3000),shifted.features().get(i).pos());
        var job=new WdaInteriorSitesJob(shifted.rooms(),4,p->m.at(p.move(-4000,-32,3000)),"dimdoors:dungeon");while(!job.step(128,100_000_000)){}
        for(int i=0;i<4;i++){var s=sites.get(i);assertEquals(new PortalRecord.Site("dimdoors:dungeon",s.x()+4000,s.y()+32,s.z()-3000),job.sites().get(i));}
        for(var p:room.passages())m.blocked.add(p);assertThrows(IllegalStateException.class,()->m.exits(1));
        assertThrows(IllegalArgumentException.class,()->PlagueLayout.validateEnvironment(new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.ROCK)));
        assertThrows(IllegalArgumentException.class,()->InteriorRoom.requireRockCover(new IslandPlan(m.reserved.building(),m.reserved.building().maxY()+4,8,6,16,IslandPlan.Surface.ROCK)));
    }
    @Test void originalNormalCellsReplaceOnlyNativePlaceholderContents() throws Exception {
        var t=templates("plague_asylum");
        try(var zip=new ZipFile(JAR.toFile())) {for(int i=0;i<7;i++) {
            try(var in=zip.getInputStream(zip.getEntry("data/dungeons_arise/structures/plague_asylum/plague_asylum_cell_normal_"+i+".nbt"))) {
                var n=net.minecraft.nbt.NbtIo.readCompressed(in);PlagueLayout.requireDenseNormalCell(n);
                n.getList("blocks",10).remove(0);assertThrows(IllegalArgumentException.class,()->PlagueLayout.requireDenseNormalCell(n));
            }
        }}
        for(int variant=0;variant<7;variant++) {
            var p=assemble(t,Rotation.NONE,variant);var original=features(p);var m=new Model(p);
            assertEquals(original.size()-1,m.layout.features().size());m.audit();
            var altered=new ArrayList<>(original);int i=0;
            while(!(PlagueLayout.key(p.get(altered.get(i).owner()).piece()).equals("corridor_1_cells") && altered.get(i).block().equals("minecraft:chest")))i++;
            var f=altered.get(i);altered.set(i,new PlagueLayout.Feature(f.owner(),f.pos(),"minecraft:spawner",f.data()));
            assertThrows(IllegalArgumentException.class,()->PlagueLayout.inspect(p.stream().map(Placed::piece).toList(),ENV,complete(p),sockets(p),altered));
        }
    }
    @Test void pinnedSourceHasAbsoluteUndergroundHeightAndOriginalEncounterPool() throws Exception {
        assertEquals(HASH,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
        var t=templates("plague_asylum");assertEquals(33,t.size());assertTrue(t.values().stream().allMatch(v->v.entities().isEmpty()));
        try(var zip=new ZipFile(JAR.toFile())) {
            var d=json(zip,"data/dungeons_arise/worldgen/structure/plague_asylum.json");assertEquals(0,d.getAsJsonObject("start_height").get("absolute").getAsInt());assertFalse(d.has("project_start_to_heightmap"));
            assertEquals("bury",d.get("terrain_adaptation").getAsString());assertEquals(7,d.get("size").getAsInt());assertEquals(104,d.get("max_distance_from_center").getAsInt());
        }
        for(var v:t.values())for(var pool:v.pools())assertTrue(PlagueLayout.belongs(v.id(),pool),v.id()+" / "+pool);
        var fang=t.get("evoker_fangs_0").blocks().stream().filter(b->b.id().equals("minecraft:spawner")).findFirst().orElseThrow().data();assertEquals("minecraft:evoker_fangs",fang.getCompound("SpawnData").getCompound("entity").getString("id"));
        var horde=t.get("zombie_horde_0").blocks().stream().filter(b->b.id().equals("minecraft:spawner")).findFirst().orElseThrow().data();assertEquals(20,horde.getInt("SpawnCount"));
    }
}
