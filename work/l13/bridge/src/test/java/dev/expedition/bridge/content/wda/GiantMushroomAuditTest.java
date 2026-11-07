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

class GiantMushroomAuditTest {
    private static final BlockPos ORIGIN=new BlockPos(-101,Y-6,229);
    private static final String POOLS="dungeons_arise:fungi/giant_mushroom/giant_mushroom_";
    private static final List<IslandPlan.Surface> SURFACES=List.of(IslandPlan.Surface.GRASS,IslandPlan.Surface.ROCK);
    private static final WdaStructurePlanner.Environment ENV=new WdaStructurePlanner.Environment(Y,8,6,16,SURFACES.get(0));

    private static Placed attach(Placed parent,Template child,Direction face) {
        var socket=parent.template().blocks().stream().filter(b->b.props().getString("orientation").startsWith(face.getName()+"_")
                && child.blocks().stream().anyMatch(i->i.data().getString("name").equals(b.data().getString("target"))))
                .findFirst().orElseThrow();
        return connect(parent,socket,child,face.getOpposite());
    }

    private static List<Placed> assemble(Map<String,Template> templates,String variant,Rotation rot,boolean clockwise) {
        boolean twin=variant.equals("twin");
        String prefix=variant+"_giant_mushroom_part_";
        var root=new Placed(templates.get(prefix+"0"),ORIGIN,rot);
        var pieces=new ArrayList<Placed>();
        pieces.add(root);
        for (int layer=0;layer<(twin?2:1);layer++) {
            int offset=layer*4;
            var first=layer==0 ? root : attach(root,templates.get(prefix+"4"),Direction.UP);
            if (layer==1) pieces.add(first);
            int[] order=clockwise ? new int[]{1,2,3} : new int[]{3,2,1};
            var directions=clockwise ? List.of(Direction.EAST,Direction.SOUTH,Direction.WEST)
                    : List.of(Direction.SOUTH,Direction.EAST,Direction.NORTH);
            var previous=first;
            for (int i=0;i<order.length;i++) {
                previous=attach(previous,templates.get(prefix+(offset+order[i])),directions.get(i));
                pieces.add(previous);
            }
        }
        var encounter=templates.get("skeleton_0_"+(twin?"twins":"red"));
        for (var p:List.copyOf(pieces)) for (var b:p.template().blocks()) {
            if (b.data().getString("pool").equals(POOLS+"spawners"))
                pieces.add(connect(p,b,encounter,Direction.DOWN));
        }
        return pieces;
    }

    private static final class Model extends Terrain implements GiantMushroomRoute.Probe {
        final GiantMushroomLayout layout;
        final IslandPlan destination;
        Model(List<Placed> assembly,IslandPlan.Surface surface) {
            super(assembly);
            layout=GiantMushroomLayout.inspect(assembly.stream().map(Placed::piece).toList(),
                    new WdaStructurePlanner.Environment(Y,8,6,16,surface));
            destination=new IslandPlan(island.building(),Y,8,6,16,surface);
        }
        boolean has(Cell p,String id) {
            return !blocked.contains(p) && placed.containsKey(p) && placed.get(p).id().equals("minecraft:"+id);
        }
        private Cell cell(BlockPos p) { return new Cell(p.getX(),p.getY(),p.getZ()); }
        public boolean stem(Cell p) { return has(p,"mushroom_stem"); }
        public boolean lootChest(BlockPos pos,String table) {
            var p=cell(pos);
            return has(p,"chest") && placed.get(p).data().getString("LootTable").equals(table) && at(p.move(0,1,0))==Type.AIR;
        }
        public boolean skeletonSpawner(BlockPos pos) {
            var p=cell(pos);
            return has(p,"spawner") && placed.get(p).data().getCompound("SpawnData").getCompound("entity").getString("id").equals("minecraft:skeleton");
        }
        void survey(int exits) {
            var step=GiantMushroomRoute.survey(layout,destination,exits,this);
            for (int i=0;i<16000;i++) if (step.getAsBoolean()) return;
            fail("Giant mushroom survey exceeded model budget");
        }
    }

    @Test void bothOriginalFormsReachTheirStemLedgesFromAllRequestedExits() throws Exception {
        var templates=templates("giant_mushroom");
        var cases=new ArrayList<Map<String,Object>>();
        var seen=new HashSet<String>();
        for (var surface:SURFACES) for (var rot:Rotation.values()) for (var name:List.of("red","twin")) for (boolean cw:List.of(true,false)) {
            var parts=assemble(templates,name,rot,cw);
            var model=new Model(parts,surface);
            parts.forEach(p->seen.add(p.template().id()));
            for (int exits=1;exits<=4;exits++) model.survey(exits);
            var loot=model.placed.values().stream().filter(s->s.data().contains("LootTable")).count();
            var mobs=model.placed.values().stream().filter(s->s.id().equals("minecraft:spawner")).count();
            assertEquals(name.equals("red")?1:2,loot); assertEquals(loot,mobs);
            for (var part:parts) for (var block:part.template().blocks()) {
                if (!block.data().contains("LootTable") && !block.id().equals("minecraft:spawner")) continue;
                var p=part.at(block.pos());
                assertEquals(block.data(),model.placed.get(new Cell(p.getX(),p.getY(),p.getZ())).data());
            }
            var row=new LinkedHashMap<String,Object>();
            row.put("surface",surface); row.put("rotation",rot); row.put("form",name); row.put("assemblyOrder",cw?"clockwise":"counterclockwise");
            row.put("pieces",parts.size()); row.put("riseAboveSurface",name.equals("red")?5:6);
            row.put("lootChests",loot); row.put("skeletonSpawners",mobs); row.put("exitCounts",List.of(1,2,3,4)); cases.add(row);
        }
        assertEquals(32,cases.size()); assertEquals(templates.keySet(),seen);
        var report=new LinkedHashMap<String,Object>();
        report.put("wdaSha256",HASH); report.put("runtimeAccepted",false);
        report.put("scope","Two native forms assembled from original connectors in either ring order, four rotations and two dry surfaces. Verifies first climb and native encounter/loot placement. Upper traversal, random native generation and multiplayer pending.");
        report.put("cases",cases);
        var file=Path.of("build/reports/giant-mushroom-entry-audit.json"); Files.createDirectories(file.getParent());
        Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
    }

    @Test void everyCorePartAndEncounterIsRequiredInItsOriginalFrame() throws Exception {
        var templates=templates("giant_mushroom");
        for (var name:List.of("red","twin")) {
            var graph=assemble(templates,name,Rotation.CLOCKWISE_90,false).stream().map(Placed::piece).toList();
            for (int i=0;i<graph.size();i++) {
                var broken=new ArrayList<>(graph); var p=broken.remove(i);
                assertThrows(IllegalArgumentException.class,()->GiantMushroomLayout.inspect(broken,ENV));
                broken.add(i,new WdaTemplatePiece(p.id(),p.origin().above(),p.rotation(),p.bounds().move(0,1,0),false));
                assertThrows(IllegalArgumentException.class,()->GiantMushroomLayout.inspect(broken,ENV));
                broken.set(i,new WdaTemplatePiece(p.id(),p.origin(),p.rotation().getRotated(Rotation.CLOCKWISE_90),p.bounds(),false));
                assertThrows(IllegalArgumentException.class,()->GiantMushroomLayout.inspect(broken,ENV));
                broken.set(i,new WdaTemplatePiece(p.id(),p.origin(),p.rotation(),p.bounds().expand(1),false));
                assertThrows(IllegalArgumentException.class,()->GiantMushroomLayout.inspect(broken,ENV));
            }
            var wrong=new ArrayList<>(graph); wrong.set(wrong.size()-1,graph.get(0));
            assertThrows(IllegalArgumentException.class,()->GiantMushroomLayout.inspect(wrong,ENV));
            var last=graph.get(graph.size()-1);
            wrong.set(wrong.size()-1,new WdaTemplatePiece(last.id().replace("skeleton_0_", "wrong_"),last.origin(),last.rotation(),last.bounds(),false));
            assertThrows(IllegalArgumentException.class,()->GiantMushroomLayout.inspect(wrong,ENV));
        }
    }

    @Test void routeRejectsBlockedExitsDamagedFootholdsAndAlteredRewards() throws Exception {
        var templates=templates("giant_mushroom");
        for (var name:List.of("red","twin")) {
            var m=new Model(assemble(templates,name,Rotation.COUNTERCLOCKWISE_90,true),SURFACES.get(0));
            var w=m.layout;
            var blocks=new ArrayList<Cell>();
            blocks.add(m.destination.perimeterSites(4).get(3));
            blocks.add(w.cell(name.equals("red")?new BlockPos(30,10,31):new BlockPos(21,11,34)));
            blocks.add(w.cell(name.equals("red")?new BlockPos(30,9,32):new BlockPos(15,9,31)));
            for (var p:w.form().chests) { blocks.add(w.cell(p)); blocks.add(w.cell(p.above())); }
            for (var p:w.form().spawners) blocks.add(w.cell(p));
            for (var p:blocks) {
                m.blocked.add(p); assertThrows(IllegalStateException.class,()->m.survey(4),"Obstruction at "+p); m.blocked.clear();
            }
            var chest=w.cell(w.form().chests.get(0)); var state=m.placed.get(chest);
            var data=state.data().copy(); data.putString("LootTable","minecraft:empty");
            m.placed.put(chest,new State(state.id(),state.props(),state.facing(),data));
            assertThrows(IllegalStateException.class,()->m.survey(1));
        }
    }

    @Test void refusesAnOffGradeOrWetIslandAndRelocatesAllLandmarks() throws Exception {
        var parts=assemble(templates("giant_mushroom"),"twin",Rotation.CLOCKWISE_180,true);
        var graph=parts.stream().map(Placed::piece).toList();
        assertThrows(IllegalArgumentException.class,()->GiantMushroomLayout.inspect(graph,new WdaStructurePlanner.Environment(Y+1,8,6,16,SURFACES.get(0))));
        assertThrows(IllegalArgumentException.class,()->GiantMushroomLayout.inspect(graph,new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.WATER,2)));
        var layout=GiantMushroomLayout.inspect(graph,ENV); var moved=layout.move(105,-16,-262);
        for (var part:parts) for (var b:part.template().blocks()) if (b.data().contains("SpawnData") || b.data().contains("LootTable")) {
            var p=part.at(b.pos()); var local=p.subtract(layout.origin()).rotate(Rotation.CLOCKWISE_180);
            assertEquals(p.offset(105,-16,-262),moved.position(local));
        }
    }

    @Test void pinnedPoolsContainCompleteFormsAndTheOriginalArmedSkeletons() throws Exception {
        assertEquals(HASH,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
        var templates=templates("giant_mushroom");
        assertEquals(14,templates.size());
        try (var zip=new ZipFile(JAR.toFile())) {
            String wg="data/dungeons_arise/worldgen/";
            var def=json(zip,wg+"structure/giant_mushroom.json");
            assertEquals("minecraft:jigsaw",def.get("type").getAsString()); assertEquals(POOLS+"start",def.get("start_pool").getAsString());
            assertEquals("WORLD_SURFACE_WG",def.get("project_start_to_heightmap").getAsString());
            assertEquals(-5,def.getAsJsonObject("start_height").get("absolute").getAsInt());
            assertFalse(def.has("terrain_adaptation"));
            for (var pool:List.of("start","main","spawners")) {
                var p=json(zip,wg+"template_pool/fungi/giant_mushroom/giant_mushroom_"+pool+".json");
                var entries=p.getAsJsonArray("elements"); assertEquals(pool.equals("main")?12:2,entries.size());
                assertEquals("minecraft:empty",p.get("fallback").getAsString());
                for (var raw:entries) {
                    var entry=raw.getAsJsonObject(); var el=entry.getAsJsonObject("element");
                    assertEquals("minecraft:single_pool_element",el.get("element_type").getAsString()); assertEquals(1,entry.get("weight").getAsInt());
                    assertEquals("rigid",el.get("projection").getAsString()); assertEquals("minecraft:empty",el.get("processors").getAsString());
                    if (pool.equals("start")) assertTrue(Set.of(GiantMushroomLayout.Form.RED.part(0),GiantMushroomLayout.Form.TWIN.part(0)).contains(el.get("location").getAsString()));
                }
            }
        }
        for (var name:List.of("red","twins")) {
            var spawner=templates.get("skeleton_0_"+name).blocks().stream().filter(b->b.id().equals("minecraft:spawner")).findFirst().orElseThrow();
            var mob=spawner.data().getCompound("SpawnData").getCompound("entity");
            assertEquals("minecraft:skeleton",mob.getString("id")); assertEquals(40,mob.getFloat("Health"));
            assertEquals(mob,spawner.data().getList("SpawnPotentials",Tag.TAG_COMPOUND).getCompound(0).getCompound("data").getCompound("entity"));
            var hands=mob.getList("HandItems",Tag.TAG_COMPOUND);
            assertEquals("minecraft:bow",hands.getCompound(0).getString("id"));
            assertEquals("minecraft:punch",hands.getCompound(0).getCompound("tag").getList("Enchantments",Tag.TAG_COMPOUND).getCompound(0).getString("id"));
            assertEquals("minecraft:tipped_arrow",hands.getCompound(1).getString("id"));
            assertEquals(2,hands.getCompound(1).getCompound("tag").getList("CustomPotionEffects",Tag.TAG_COMPOUND).getCompound(0).getInt("Id"));
        }
    }
}
