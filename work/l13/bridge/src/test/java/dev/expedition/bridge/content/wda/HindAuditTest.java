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

class HindAuditTest {
    private static WdaStructurePlanner.Environment env(int y,IslandPlan.Surface s){return new WdaStructurePlanner.Environment(y,8,6,16,s);}
    private static List<Placed> assemble(Map<String,Template> t,Rotation r,int ground){
        var root=new Placed(t.get("part_1"),new BlockPos(-113,ground-17,221),r);
        return List.of(root,connect(root,root.template().joint("ceryneian_hind_side_0",Direction.WEST),t.get("part_0"),Direction.EAST),
                connect(root,root.template().joint("ceryneian_hind_side_3",Direction.EAST),t.get("part_2"),Direction.WEST));
    }
    private static Cell cell(BlockPos p){return new Cell(p.getX(),p.getY(),p.getZ());}
    private static String axis(Block b,Rotation r){String a=b.props().getString("axis");return a.isEmpty()?"":r.rotate(Direction.fromAxisAndDirection(Direction.Axis.byName(a),Direction.AxisDirection.POSITIVE)).getAxis().getName();}
    private static List<HindLayout.Feature> features(List<Placed> parts){
        var result=new ArrayList<HindLayout.Feature>();for(var p:parts)for(var b:p.template().blocks())if(!b.id().equals("minecraft:jigsaw")){
            var facing=Direction.byName(b.props().getString("facing"));result.add(new HindLayout.Feature(cell(p.at(b.pos())),b.id(),axis(b,p.rotation()),
                    facing==null?"":p.rotation().rotate(facing).getName(),b.data().getString("LootTable")));
        }return result;
    }
    private static HindLayout inspect(List<Placed> p,int ground){return HindLayout.inspect(p.stream().map(Placed::piece).toList(),env(ground,IslandPlan.Surface.ROCK),features(p));}
    private static class Model extends Terrain implements HindRoute.Probe {
        final HindLayout layout;
        final IslandPlan reserved;
        int featureChecks;
        Model(List<Placed> parts,IslandPlan.Surface surface,int ground){
            super(parts);layout=inspect(parts,ground);reserved=new IslandPlan(island.building(),ground,8,6,16,surface);
            for(var p:parts)for(var b:p.template().blocks())if(b.id().equals("minecraft:bone_block")){
                var c=cell(p.at(b.pos()));var previous=placed.get(c);var props=previous.props().copy();props.putString("axis",axis(b,p.rotation()));
                placed.put(c,new State(previous.id(),props,previous.facing(),previous.data()));
            }
        }
        @Override public Type at(Cell p){if(blocked.contains(p))return Type.BLOCKED;var s=placed.get(p);
            if(s==null)return p.y()<layout.groundY()?Type.SUPPORT:Type.AIR;
            return s.id().equals("minecraft:smooth_quartz")?Type.SUPPORT:super.at(p);
        }
        public boolean feature(HindLayout.Feature f){
            featureChecks++;var s=placed.get(f.pos());if(blocked.contains(f.pos())||s==null||!s.id().equals(f.block()))return false;
            if(s.id().equals("minecraft:bone_block"))return s.props().getString("axis").equals(f.axis());
            if(s.id().equals("minecraft:chest"))return s.facing().getName().equals(f.facing())&&s.props().getString("type").equals("single")
                    &&s.props().getString("waterlogged").equals("false")&&s.data().getString("LootTable").equals(f.loot());
            return true;
        }
        void finish(HindRoute route){for(int i=0;i<40000;i++)if(route.step())return;fail("Fossil survey budget exceeded");}
        void survey(int exits){finish(new HindRoute(layout,reserved,exits,this));}
    }
    @Test void allSectionsRemainBuriedAtOriginalDepthWithDryAccessAndNoExcavation() throws Exception {
        var templates=templates("ceryneian_hind");var rows=new ArrayList<Map<String,Object>>();
        for(var r:Rotation.values())for(var surface:List.of(IslandPlan.Surface.GRASS,IslandPlan.Surface.ROCK))for(int ground:new int[]{Y,Y+32}){
            var parts=assemble(templates,r,ground);var m=new Model(parts,surface,ground);var before=new HashMap<>(m.placed);
            for(int exits=1;exits<=4;exits++)m.survey(exits);
            assertEquals(before,m.placed);assertEquals(4523,m.layout.features().size());assertEquals(4523*4,m.featureChecks);
            assertEquals(144,Math.max(m.reserved.building().width(),m.reserved.building().depth()));
            assertEquals(48,Math.min(m.reserved.building().width(),m.reserved.building().depth()));
            for(int i=0;i<parts.size();i++)for(int j=i+1;j<parts.size();j++)assertFalse(parts.get(i).bounds().intersects(parts.get(j).bounds()));
            var chest=m.layout.features().stream().filter(f->f.block().equals("minecraft:chest")).findFirst().orElseThrow();
            assertEquals(ground-7,chest.pos().y());assertEquals(Type.SUPPORT,m.at(chest.pos().move(0,1,0)),"Do not automatically expose buried treasure");
            assertEquals(Type.AIR,m.at(m.layout.at(26,10,23)),"Retain the one original underground air cell");
            for(var p:m.layout.approaches())assertTrue(InteriorSiteSearch.clearPlatform(p,m));
            rows.add(Map.of("rotation",r,"surface",surface,"firstFreeY",ground,"pieces",3,"originalBlocksChecked",4523,"treasureDepth",7,
                    "goldBlocks",2,"exitCounts",List.of(1,2,3,4),"addedOrRemovedBlocks",0));
        }
        var report=new LinkedHashMap<String,Object>();report.put("jarSha256",HASH);report.put("structure",HindLayout.TARGET);report.put("cases",rows);
        report.put("sourceTemplates",new TreeSet<>(templates.keySet()));report.put("nativeGeneratorExecuted",false);report.put("runtimeTested",false);
        report.put("scope","16 complete original-template assemblies: four rotations, two dry surfaces and two planning heights; each checks 1..4 exits and all 4523 original non-jigsaw blocks. Access is beside all three exposed fossil sections; treasure remains buried. Native generation, collisions, excavation and persistence untested.");
        Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/hind-entry-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    @Test void missingDuplicatedMisalignedRotatedOrResizedSectionsRejectBeforeAllocation() throws Exception {
        var parts=assemble(templates("ceryneian_hind"),Rotation.CLOCKWISE_90,Y);
        for(int i=0;i<3;i++){
            var missing=new ArrayList<>(parts);missing.remove(i);assertThrows(IllegalArgumentException.class,()->inspect(missing,Y));
            var moved=new ArrayList<>(parts);var p=parts.get(i);moved.set(i,new Placed(p.template(),p.origin().offset(0,1,0),p.rotation()));assertThrows(IllegalArgumentException.class,()->inspect(moved,Y));
            var rotated=new ArrayList<>(parts);rotated.set(i,new Placed(p.template(),p.origin(),p.rotation().getRotated(Rotation.CLOCKWISE_90)));assertThrows(IllegalArgumentException.class,()->inspect(rotated,Y));
        }
        var duplicated=new ArrayList<>(parts);duplicated.set(2,parts.get(1));assertThrows(IllegalArgumentException.class,()->inspect(duplicated,Y));
        var wrongRoot=new ArrayList<>(parts);Collections.swap(wrongRoot,0,1);assertThrows(IllegalArgumentException.class,()->inspect(wrongRoot,Y));
        var pieces=new ArrayList<>(parts.stream().map(Placed::piece).toList());var p=pieces.get(1);
        pieces.set(1,new WdaTemplatePiece(p.id(),p.origin(),p.rotation(),p.bounds().expand(1),false));assertThrows(IllegalArgumentException.class,()->HindLayout.inspect(pieces,env(Y,IslandPlan.Surface.ROCK),features(parts)));
        var changed=new ArrayList<>(features(parts));changed.remove(0);assertThrows(IllegalArgumentException.class,()->HindLayout.inspect(parts.stream().map(Placed::piece).toList(),env(Y,IslandPlan.Surface.ROCK),changed));
    }
    @Test void changedFossilGoldAirTreasureOrBoneAxisRejectWithoutRepair() throws Exception {
        var parts=assemble(templates("ceryneian_hind"),Rotation.CLOCKWISE_90,Y);
        for(String id:List.of("minecraft:bone_block","minecraft:smooth_quartz","minecraft:gold_block","minecraft:air","minecraft:chest")){
            var m=new Model(parts,IslandPlan.Surface.ROCK,Y);var f=m.layout.features().stream().filter(x->x.block().equals(id)).findFirst().orElseThrow();
            m.placed.put(f.pos(),new State("minecraft:stone",new CompoundTag(),null,new CompoundTag()));var before=new HashMap<>(m.placed);
            assertThrows(IllegalStateException.class,()->m.survey(1));assertEquals(before,m.placed);
        }
        for(boolean bone:new boolean[]{false,true}){
            var m=new Model(parts,IslandPlan.Surface.ROCK,Y);var f=m.layout.features().stream().filter(x->x.block().equals(bone?"minecraft:bone_block":"minecraft:chest")).findFirst().orElseThrow();
            var old=m.placed.get(f.pos());var props=old.props().copy();var data=old.data().copy();
            if(bone)props.putString("axis",f.axis().equals("x")?"z":"x");else data.putString("LootTable","minecraft:chests/simple_dungeon");
            m.placed.put(f.pos(),new State(old.id(),props,old.facing(),data));assertThrows(IllegalStateException.class,()->m.survey(1));
        }
    }
    @Test void everySectionAndRequestedExitMustStayReachableIncludingLateObstruction() throws Exception {
        var parts=assemble(templates("ceryneian_hind"),Rotation.NONE,Y);
        for(int i=0;i<3;i++){var m=new Model(parts,IslandPlan.Surface.ROCK,Y);m.blocked.add(m.layout.approaches().get(i));assertThrows(IllegalStateException.class,()->m.survey(1));}
        var exit=new Model(parts,IslandPlan.Surface.GRASS,Y);exit.blocked.add(exit.reserved.perimeterSites(4).get(3));assertThrows(IllegalStateException.class,()->exit.survey(4));
        var late=new Model(parts,IslandPlan.Surface.ROCK,Y);var route=new HindRoute(late.layout,late.reserved,4,late);
        for(int i=0;late.featureChecks==0&&i<40000;i++)assertFalse(route.step());assertEquals(1,late.featureChecks);
        late.blocked.add(late.layout.approaches().get(0));assertThrows(IllegalStateException.class,()->late.finish(route));
    }
    @Test void relocationRetainsBurialAndInventoryAndWetOrShiftedGroundIsRejected() throws Exception {
        for(var rotation:Rotation.values()){
            var parts=assemble(templates("ceryneian_hind"),rotation,Y);var m=new Model(parts,IslandPlan.Surface.ROCK,Y);var moved=m.layout.move(4000,19,-3000);
            assertEquals(Y+19,moved.groundY());assertEquals(m.layout.approaches().stream().map(p->p.move(4000,19,-3000)).toList(),moved.approaches());
            for(int i=0;i<moved.features().size();i++){var a=m.layout.features().get(i);var b=moved.features().get(i);assertEquals(a.pos().move(4000,19,-3000),b.pos());assertEquals(a.axis(),b.axis());assertEquals(a.facing(),b.facing());assertEquals(a.loot(),b.loot());}
            var raised=new IslandPlan(m.reserved.building(),Y+1,8,6,16,IslandPlan.Surface.ROCK);assertThrows(IllegalStateException.class,()->new HindRoute(m.layout,raised,1,m));
            assertThrows(IllegalArgumentException.class,()->HindLayout.inspect(parts.stream().map(Placed::piece).toList(),new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.WATER,3),features(parts)));
        }
    }
    @Test void pinnedDesertDefinitionOriginalConnectorsLootAndEmptyEncountersStayIntact() throws Exception {
        assertEquals(HASH,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
        try(var zip=new ZipFile(JAR.toFile())){
            var def=json(zip,"data/dungeons_arise/worldgen/structure/ceryneian_hind.json");assertEquals(-16,def.getAsJsonObject("start_height").get("absolute").getAsInt());assertFalse(def.has("terrain_adaptation"));
            assertEquals("WORLD_SURFACE_WG",def.get("project_start_to_heightmap").getAsString());assertEquals("dungeons_arise:desertic/ceryneian_hind/ceryneian_hind_start",def.get("start_pool").getAsString());
            var pool=json(zip,"data/dungeons_arise/worldgen/template_pool/desertic/ceryneian_hind/ceryneian_hind_start.json");assertEquals("dungeons_arise:aquatic/ceryneian_hind/ceryneian_hind_start",pool.get("name").getAsString());
            var t=templates("ceryneian_hind");assertEquals(3,t.size());for(var p:t.values()){assertTrue(p.entities().isEmpty());assertFalse(p.legacy());assertTrue(p.blocks().stream().noneMatch(b->b.id().equals("minecraft:spawner")));
                for(var b:p.blocks())if(b.id().equals("minecraft:jigsaw")){assertEquals("minecraft:structure_void",b.data().getString("final_state"));assertTrue(b.data().getString("pool").startsWith("dungeons_arise:desertic/"));}}
            var treasure=t.get("part_0").blocks().stream().filter(b->b.id().equals("minecraft:chest")).findFirst().orElseThrow();assertEquals(new BlockPos(26,10,24),treasure.pos());assertEquals("dungeons_arise:chests/ceryneian_hind/ceryneian_hind_treasure",treasure.data().getString("LootTable"));
        }
    }
}
