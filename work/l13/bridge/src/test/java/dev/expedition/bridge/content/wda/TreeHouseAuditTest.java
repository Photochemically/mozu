package dev.expedition.bridge.content.wda;

import com.google.gson.GsonBuilder;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import static dev.expedition.bridge.content.wda.WdaTemplateFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class TreeHouseAuditTest {
    private static final BlockPos ORIGIN=new BlockPos(-83,Y-6,219);
    private static final String POOL="dungeons_arise:jungle/jungle_tree_house/jungle_tree_house_";
    private static final WdaStructurePlanner.Environment ENV=new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.GRASS);

    private static List<Placed> assemble(Map<String,Template> templates,boolean rootsFirst,Rotation rotation,int mask) {
        var root=new Placed(templates.get(rootsFirst?"1_roots":"0_main"),ORIGIN,rotation);
        var face=rootsFirst?Direction.UP:Direction.DOWN;
        var socket=root.template().joint(rootsFirst?"jungle_tree_house_part_0":"jungle_tree_house_part_1",face);
        var child=connect(root,socket,templates.get(rootsFirst?"0_main":"1_roots"),face.getOpposite());
        var body=rootsFirst?child:root;
        var pieces=new ArrayList<>(List.of(root,child));
        int bit=0;
        for (var joint:body.template().blocks()) if (joint.data().getString("pool").equals(POOL+"spawners")) {
            var encounter=templates.get((mask&(1<<bit++))==0?"husk_0":"skeleton_0");
            pieces.add(connect(body,joint,encounter,Direction.DOWN));
        }
        assertEquals(11,bit);
        return pieces;
    }

    private static final class Model extends Terrain implements TreeHouseRoute.Probe {
        final TreeHouseLayout tree;
        final IslandPlan destination;
        final List<TreeHouseLayout.Spawn> entities=new ArrayList<>();
        Model(List<Placed> parts,IslandPlan.Surface surface) {
            super(parts);
            tree=TreeHouseLayout.inspect(parts.stream().map(Placed::piece).toList(),
                    new WdaStructurePlanner.Environment(Y,8,6,16,surface));
            destination=new IslandPlan(island.building(),tree.groundY(),8,6,16,surface);
            for (var part:parts) for (int i=0;i<part.template().entities().size();i++) {
                var entity=part.template().entities().getCompound(i);
                var local=entity.getList("blockPos",Tag.TAG_INT);
                var coords=entity.getList("pos",Tag.TAG_DOUBLE);
                assertEquals(0.5,coords.getDouble(0)); assertEquals(1,coords.getDouble(1)); assertEquals(0.5,coords.getDouble(2));
                var pos=part.at(new BlockPos(local.getInt(0),local.getInt(1),local.getInt(2)));
                entities.add(new TreeHouseLayout.Spawn(pos,entity.getCompound("nbt").getString("id")));
            }
        }
        @Override public Type at(Cell p) {
            if (blocked.contains(p) || placed.containsKey(p)) return super.at(p);
            return p.y()<tree.groundY()?Type.SUPPORT:Type.AIR;
        }
        private Cell cell(BlockPos p) { return new Cell(p.getX(),p.getY(),p.getZ()); }
        boolean has(Cell p,String id) { return !blocked.contains(p) && placed.containsKey(p) && placed.get(p).id().equals("minecraft:"+id); }
        public boolean originalSupport(Cell p) {
            if (blocked.contains(p) || !placed.containsKey(p)) return false;
            var s=placed.get(p);
            if (s.props().getString("waterlogged").equals("true")) return false;
            if (s.id().equals("minecraft:jungle_leaves")) return s.props().getString("persistent").equals("true");
            return Set.of("minecraft:jungle_wood","minecraft:bone_block","minecraft:dark_oak_planks").contains(s.id());
        }
        public boolean loot(BlockPos pos,String kind,String table) {
            var p=cell(pos);
            return has(p,kind) && placed.get(p).data().getString("LootTable").equals(table);
        }
        public boolean ladder(BlockPos pos,Direction facing) {
            var p=cell(pos);
            return has(p,"ladder") && placed.get(p).facing()==facing && placed.get(p).props().getString("waterlogged").equals("false")
                    && originalSupport(p.move(-facing.getStepX(),0,-facing.getStepZ()));
        }
        void survey(int exits) {
            if (!tree.matchesEntities(entities)) throw new IllegalStateException("Invalid placed inhabitants");
            var route=TreeHouseRoute.survey(tree,destination,exits,this);
            for (int i=0;i<14000;i++) if (route.getAsBoolean()) return;
            fail("Tree house route exceeded model budget");
        }
    }

    @Test void bothNativeStartingPiecesReachTheOriginalPlatform() throws Exception {
        var templates=templates("jungle_tree_house");
        var cases=new ArrayList<Map<String,Object>>();
        for (var surface:List.of(IslandPlan.Surface.GRASS,IslandPlan.Surface.ROCK)) for (boolean rootsFirst:List.of(false,true))
            for (var rotation:Rotation.values()) for (int mask:List.of(0,2047,682,1365)) {
                var parts=assemble(templates,rootsFirst,rotation,mask);
                var m=new Model(parts,surface);
                assertEquals(rootsFirst?Y+16:Y,m.tree.groundY());
                assertEquals(13,parts.size()); assertEquals(11,m.entities.size());
                for (int exits=1;exits<=4;exits++) m.survey(exits);
                assertEquals(9,m.placed.values().stream().filter(s->s.data().contains("LootTable")).count());
                assertEquals(0,m.placed.values().stream().filter(s->s.id().equals("minecraft:spawner")).count());
                var body=parts.get(rootsFirst?1:0);
                for (var block:body.template().blocks()) if (block.data().contains("LootTable")) {
                    var p=body.at(block.pos());
                    assertEquals(block.data(),m.placed.get(new Cell(p.getX(),p.getY(),p.getZ())).data());
                }
                var row=new LinkedHashMap<String,Object>();
                row.put("surface",surface); row.put("startingPiece",rootsFirst?"1_roots":"0_main"); row.put("rotation",rotation);
                row.put("skeletonMask",mask); row.put("islandGroundAdjustment",rootsFirst?16:0); row.put("platformRise",18);
                row.put("entities",11); row.put("lootContainers",9); row.put("exitCounts",List.of(1,2,3,4)); cases.add(row);
            }
        assertEquals(64,cases.size());
        var report=new LinkedHashMap<String,Object>();
        report.put("wdaSha256",HASH); report.put("runtimeAccepted",false); report.put("encounterAssignments",2048);
        report.put("scope","64 source-NBT assemblies with both native starting pieces, four rotations, two dry surfaces and four inhabitant mixes. Checks original branch/leaf route to the first wooden platform. Actual generation, upper rooms, combat and persistence pending.");
        report.put("cases",cases);
        var file=Path.of("build/reports/tree-house-entry-audit.json"); Files.createDirectories(file.getParent());
        Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
    }

    @Test void allEncounterAssignmentsFitTheOriginalAlignedSockets() throws Exception {
        var templates=templates("jungle_tree_house");
        for (int mask=0;mask<2048;mask++) {
            var parts=assemble(templates,false,Rotation.NONE,mask);
            var tree=TreeHouseLayout.inspect(parts.stream().map(Placed::piece).toList(),ENV);
            assertEquals(Integer.bitCount(mask),tree.inhabitants().stream().filter(p->p.type().equals("minecraft:skeleton")).count());
        }
    }

    @Test void rejectsMissingMisalignedOrSwappedOriginalPieces() throws Exception {
        var graph=assemble(templates("jungle_tree_house"),true,Rotation.CLOCKWISE_90,682).stream().map(Placed::piece).toList();
        for (int i=0;i<graph.size();i++) {
            var broken=new ArrayList<>(graph); var p=broken.remove(i);
            assertThrows(IllegalArgumentException.class,()->TreeHouseLayout.inspect(broken,ENV));
            broken.add(i,new WdaTemplatePiece(p.id(),p.origin().above(),p.rotation(),p.bounds().move(0,1,0),false));
            assertThrows(IllegalArgumentException.class,()->TreeHouseLayout.inspect(broken,ENV));
            broken.set(i,new WdaTemplatePiece(p.id(),p.origin(),p.rotation().getRotated(Rotation.CLOCKWISE_90),p.bounds(),false));
            assertThrows(IllegalArgumentException.class,()->TreeHouseLayout.inspect(broken,ENV));
        }
        var wrong=new ArrayList<>(graph); wrong.set(12,graph.get(11));
        assertThrows(IllegalArgumentException.class,()->TreeHouseLayout.inspect(wrong,ENV));
        assertThrows(IllegalArgumentException.class,()->TreeHouseLayout.inspect(graph,new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.WATER,2)));
    }

    @Test void blocksDamagedBranchesNonpersistentLeavesMissingLootAndBrokenLadders() throws Exception {
        var m=new Model(assemble(templates("jungle_tree_house"),true,Rotation.COUNTERCLOCKWISE_90,0),IslandPlan.Surface.GRASS);
        var tree=m.tree;
        var cells=new ArrayList<Cell>(List.of(m.destination.perimeterSites(4).get(3),tree.cell(new BlockPos(24,6,14)),
                tree.cell(new BlockPos(24,7,14)),tree.cell(new BlockPos(23,9,14)),tree.cell(new BlockPos(15,30,15))));
        for (var loot:TreeHouseLayout.LOOT) cells.add(tree.cell(loot.position()));
        for (var c:cells) {
            m.blocked.add(c); assertThrows(IllegalStateException.class,()->m.survey(4),"Obstruction at "+c); m.blocked.clear();
        }
        var leaf=tree.cell(new BlockPos(13,21,13)); var state=m.placed.get(leaf);
        assertEquals("minecraft:jungle_leaves",state.id());
        var props=state.props().copy(); props.putString("persistent","false");
        m.placed.put(leaf,new State(state.id(),props,state.facing(),state.data()));
        assertThrows(IllegalStateException.class,()->m.survey(1));
    }

    @Test void emittedEntityTypesPositionsAndCountsMustMatchAfterRelocation() throws Exception {
        var m=new Model(assemble(templates("jungle_tree_house"),false,Rotation.CLOCKWISE_180,682),IslandPlan.Surface.ROCK);
        var tree=m.tree; var actual=m.entities;
        assertTrue(tree.matchesEntities(actual));
        assertFalse(tree.matchesEntities(actual.subList(1,11)));
        var duplicate=new ArrayList<>(actual); duplicate.set(10,actual.get(0));
        assertFalse(tree.matchesEntities(duplicate));
        var p=actual.get(0); var wrong=new ArrayList<>(actual);
        wrong.set(0,new TreeHouseLayout.Spawn(p.position(),"minecraft:pig")); assertFalse(tree.matchesEntities(wrong));
        wrong.set(0,new TreeHouseLayout.Spawn(p.position().above(),p.type())); assertFalse(tree.matchesEntities(wrong));
        var moved=tree.move(-43,20,171);
        var shifted=actual.stream().map(e->new TreeHouseLayout.Spawn(e.position().offset(-43,20,171),e.type())).toList();
        assertTrue(moved.matchesEntities(shifted)); assertFalse(moved.matchesEntities(actual));
        assertEquals(tree.groundY()+20,moved.groundY());
        assertEquals(tree.pos(new BlockPos(18,24,9)).offset(-43,20,171),moved.pos(new BlockPos(18,24,9)));
    }

    @Test void pinnedNativeStartPoolIncludesRootsAndOriginalMobPayloads() throws Exception {
        assertEquals(HASH,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
        var templates=templates("jungle_tree_house"); assertEquals(4,templates.size());
        try (var zip=new ZipFile(JAR.toFile())) {
            String wg="data/dungeons_arise/worldgen/";
            var def=json(zip,wg+"structure/jungle_tree_house.json");
            assertEquals(POOL+"main",def.get("start_pool").getAsString());
            assertEquals("minecraft:jigsaw",def.get("type").getAsString());
            assertEquals(-5,def.getAsJsonObject("start_height").get("absolute").getAsInt());
            assertEquals("WORLD_SURFACE_WG",def.get("project_start_to_heightmap").getAsString());
            assertFalse(def.has("terrain_adaptation"));
            var main=json(zip,wg+"template_pool/jungle/jungle_tree_house/jungle_tree_house_main.json").getAsJsonArray("elements");
            assertEquals(2,main.size());
            assertEquals(Set.of(TreeHouseLayout.PREFIX+"0_main",TreeHouseLayout.PREFIX+"1_roots"),
                    Set.of(main.get(0).getAsJsonObject().getAsJsonObject("element").get("location").getAsString(),
                            main.get(1).getAsJsonObject().getAsJsonObject("element").get("location").getAsString()));
            for (var pool:List.of("main","spawners","start")) {
                var elements=json(zip,wg+"template_pool/jungle/jungle_tree_house/jungle_tree_house_"+pool+".json").getAsJsonArray("elements");
                assertEquals(pool.equals("start")?1:2,elements.size());
                for (var raw:elements) {
                    var el=raw.getAsJsonObject().getAsJsonObject("element");
                    assertEquals("minecraft:single_pool_element",el.get("element_type").getAsString());
                    assertEquals("rigid",el.get("projection").getAsString()); assertEquals("minecraft:empty",el.get("processors").getAsString());
                    assertEquals(1,raw.getAsJsonObject().get("weight").getAsInt());
                }
            }
        }
        for (var kind:List.of("husk","skeleton")) {
            var t=templates.get(kind+"_0"); assertEquals(1,t.blocks().size()); assertEquals(1,t.entities().size());
            var mob=t.entities().getCompound(0).getCompound("nbt");
            assertEquals("minecraft:"+kind,mob.getString("id")); assertEquals(40,mob.getFloat("Health"));
            assertTrue(mob.getBoolean("PersistenceRequired")); assertFalse(mob.getBoolean("CanPickUpLoot"));
            assertEquals(kind.equals("husk")?"minecraft:stone_axe":"minecraft:bow",mob.getList("HandItems",Tag.TAG_COMPOUND).getCompound(0).getString("id"));
            assertEquals("minecraft:chainmail_chestplate",mob.getList("ArmorItems",Tag.TAG_COMPOUND).getCompound(2).getString("id"));
        }
    }
}
