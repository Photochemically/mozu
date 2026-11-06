package dev.expedition.bridge.content.wda;

import com.google.gson.GsonBuilder;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
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

class GroundHouseAuditTest {
    private static final WdaStructurePlanner.Environment ENV=new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.GRASS);
    private static final BlockPos ORIGIN=new BlockPos(-75,Y-1,219);

    private static List<Placed> prairie(Map<String,Template> templates, String variant, Rotation rotation) {
        var root=new Placed(templates.get(variant+"_0_floor"),ORIGIN,rotation);
        String target="small_prairie_house_side_1_"+(variant.equals("clean")?"clean":"ruined");
        return List.of(root,connect(root,root.template().joint(target,Direction.UP),templates.get(variant+"_0_top"),Direction.DOWN));
    }

    private static List<Placed> mushroom(Map<String,Template> templates,int bottom,int cap,Rotation rotation,boolean occupants) {
        var root=new Placed(templates.get("bottom_"+bottom),ORIGIN,rotation);
        var roof=connect(root,root.template().joint("mushroom_house_top",Direction.UP),templates.get("top_"+cap),Direction.DOWN);
        var roots=connect(root,root.template().joint("mushroom_house_root",Direction.DOWN),templates.get("roots_0"),Direction.UP);
        var pieces=new ArrayList<>(List.of(root,roof,roots));
        if (occupants) {
            int variant=rotation.ordinal()+bottom+cap;
            for (var donor:List.of(root,roof)) for (var block:donor.template().blocks()) {
                if (block.data().getString("target").equals("dungeons_arise:spawners")) {
                    pieces.add(connect(donor,block,templates.get("piglin_"+variant%3),Direction.DOWN));
                    variant++;
                }
            }
        }
        return pieces;
    }

    private static class Model extends Terrain implements GroundHouseRoute.Probe {
        final GroundHouseLayout house;
        Model(String id,List<Placed> pieces) {
            super(pieces);
            house=GroundHouseLayout.inspect(id,pieces.stream().map(Placed::piece).toList(),ENV);
        }
        boolean has(Cell p,String id) {
            var state=placed.get(p);
            return !blocked.contains(p) && state!=null && state.id().equals("minecraft:"+id);
        }
        boolean property(Cell p,String name,String value) { return placed.get(p).props().getString(name).equals(value); }
        public boolean spruceDoor(Cell p,Direction facing) {
            var top=p.move(0,1,0);
            if (!has(p,"spruce_door") || !has(top,"spruce_door")) return false;
            var lower=placed.get(p);
            var upper=placed.get(top);
            var expected=lower.props().copy();
            expected.putString("half","upper");
            return property(p,"half","lower") && lower.facing()==facing && upper.facing()==facing && expected.equals(upper.props());
        }
        public boolean spruceStair(Cell p,Direction facing) {
            return has(p,"spruce_stairs") && placed.get(p).facing()==facing && property(p,"shape","straight")
                    && property(p,"half","bottom") && property(p,"waterlogged","false");
        }
        public boolean oakHalfSlab(Cell p) {
            return has(p,"oak_slab") && property(p,"type","bottom") && property(p,"waterlogged","false");
        }
        public boolean ruinTrigger(Cell ore,Cell carpet,Cell observer,Direction facing,Cell dispenser) {
            return has(ore,"redstone_ore") && has(carpet,"brown_carpet") && has(observer,"observer")
                    && placed.get(observer).facing()==facing && has(dispenser,"dispenser") && placed.get(dispenser).facing()==Direction.UP;
        }
        void route(int exits) {
            var survey=GroundHouseRoute.survey(house,island,exits,this);
            for (int step=0;step<20000;step++) if (survey.getAsBoolean()) return;
            fail("Survey exceeded the island area");
        }
    }

    private static Map<String,Object> audit(Model model,List<Placed> pieces) {
        for (int exits=1;exits<=4;exits++) model.route(exits);
        int loot=0;
        for (var piece:pieces) for (var block:piece.template().blocks()) {
            if (!block.data().contains("LootTable")) continue;
            var p=piece.at(block.pos());
            var actual=model.placed.get(new Cell(p.getX(),p.getY(),p.getZ()));
            assertEquals(block.id(),actual.id());
            assertEquals(block.data(),actual.data());
            loot++;
        }
        var row=new LinkedHashMap<String,Object>();
        row.put("rotation",model.house.rotation().toString());
        row.put("pieces",pieces.stream().map(p->p.template().id()).toList());
        row.put("lootContainers",loot);
        row.put("exitCounts",List.of(1,2,3,4));
        return row;
    }

    private static void report(String file,List<Map<String,Object>> rows) throws Exception {
        var result=new LinkedHashMap<String,Object>();
        result.put("wdaSha256",HASH);
        result.put("runtimeAccepted",false);
        result.put("scope","Assembled original NBT on a modeled dry island. Route checks end on the interior floor. Random generation, player physics, mechanisms and combat require in-game testing.");
        result.put("cases",rows);
        var output=Path.of("build/reports",file+"-entry-audit.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
    }

    @Test void prairieDoorsAndTrapSurviveEveryRotation() throws Exception {
        var templates=templates("small_prairie_house");
        var cases=new ArrayList<Map<String,Object>>();
        for (var rotation:Rotation.values()) for (String variant:List.of("clean","ruins")) {
            var pieces=prairie(templates,variant,rotation);
            var m=new Model(GroundHouseLayout.PRAIRIE,pieces);
            var row=audit(m,pieces);
            if (variant.equals("ruins")) {
                var items=m.placed.get(m.house.at(5,0,10)).data().getList("Items",Tag.TAG_COMPOUND);
                assertEquals(1,items.size());
                var item=items.getCompound(0);
                assertEquals("minecraft:tnt",item.getString("id"));
                assertEquals(1,item.getByte("Count"));
                assertEquals(4,item.getByte("Slot"));
            }
            cases.add(row);
        }
        assertEquals(8,cases.size());
        report("prairie-house",cases);
    }

    @Test void mushroomStairReachesAllFloorVariantsUnderEveryCap() throws Exception {
        var templates=templates("mushroom_house");
        var cases=new ArrayList<Map<String,Object>>();
        var exercised=new HashSet<String>();
        for (var rotation:Rotation.values()) for (int bottom=0;bottom<4;bottom++) for (int cap=0;cap<5;cap++) {
            for (boolean occupants:List.of(false,true)) {
                var pieces=mushroom(templates,bottom,cap,rotation,occupants);
                var m=new Model(GroundHouseLayout.MUSHROOM,pieces);
                var row=audit(m,pieces);
                for (var piece:pieces) {
                    String name=piece.template().id();
                    exercised.add(name);
                    if (!name.startsWith("piglin_")) continue;
                    var p=piece.origin();
                    var spawner=m.placed.get(new Cell(p.getX(),p.getY(),p.getZ()));
                    assertEquals("minecraft:spawner",spawner.id());
                    String entity=spawner.data().getCompound("SpawnData").getCompound("entity").getString("id");
                    assertEquals(name.equals("piglin_1")?"minecraft:piglin":"minecraft:piglin_brute",entity);
                }
                cases.add(row);
            }
        }
        assertEquals(160,cases.size());
        assertEquals(templates.keySet(),exercised);
        report("mushroom-house",cases);
    }

    @Test void refusesObstructedDoorsSlabsStairsAndPerimeterExits() throws Exception {
        var t=templates("small_prairie_house");
        var clean=new Model(GroundHouseLayout.PRAIRIE,prairie(t,"clean",Rotation.CLOCKWISE_90));
        blocked(clean,List.of(clean.house.at(2,1,10),clean.house.at(2,2,10),clean.house.at(6,1,7),clean.house.at(6,2,3),clean.house.at(1,1,10)));
        var ruins=new Model(GroundHouseLayout.PRAIRIE,prairie(t,"ruins",Rotation.COUNTERCLOCKWISE_90));
        blocked(ruins,List.of(ruins.house.at(3,0,10),ruins.house.at(3,1,10),ruins.house.at(4,0,10),ruins.house.at(5,0,10),ruins.house.at(5,1,10),ruins.house.at(4,3,10)));
        var m=new Model(GroundHouseLayout.MUSHROOM,mushroom(templates("mushroom_house"),2,0,Rotation.CLOCKWISE_180,true));
        blocked(m,List.of(m.house.at(6,0,1),m.house.at(6,2,1),m.house.at(6,2,5),m.island.perimeterSites(3).get(2)));
    }
    private static void blocked(Model m,List<Cell> points) {
        for (var p:points) {
            m.blocked.add(p);
            assertThrows(IllegalStateException.class,()->m.route(3),"Obstruction at "+p);
            m.blocked.remove(p);
        }
    }

    @Test void rejectsIncompleteMisalignedAndWetLayoutsAndRelocatesEntrance() throws Exception {
        var t=templates("small_prairie_house");
        var clean=prairie(t,"clean",Rotation.NONE);
        var ruins=prairie(t,"ruins",Rotation.NONE);
        assertThrows(IllegalArgumentException.class,()->GroundHouseLayout.inspect(GroundHouseLayout.PRAIRIE,List.of(clean.get(0).piece(),ruins.get(1).piece()),ENV));
        var pieces=mushroom(templates("mushroom_house"),0,2,Rotation.CLOCKWISE_90,true);
        var data=pieces.stream().map(Placed::piece).toList();
        for (int remove:List.of(1,2)) {
            var partial=new ArrayList<>(data);
            partial.remove(remove);
            assertThrows(IllegalArgumentException.class,()->GroundHouseLayout.inspect(GroundHouseLayout.MUSHROOM,partial,ENV));
        }
        for (int i:List.of(0,1,2,3)) {
            var bad=new ArrayList<>(data);
            var p=data.get(i);
            int shift=1;
            bad.set(i,new WdaTemplatePiece(p.id(),p.origin().above(shift),p.rotation(),p.bounds().move(0,shift,0),false));
            assertThrows(IllegalArgumentException.class,()->GroundHouseLayout.inspect(GroundHouseLayout.MUSHROOM,bad,ENV));
        }
        var spawner=data.get(3);
        var rotated=new ArrayList<>(data);
        rotated.set(3,new WdaTemplatePiece(spawner.id(),spawner.origin(),spawner.rotation().getRotated(Rotation.CLOCKWISE_90),spawner.bounds(),false));
        assertThrows(IllegalArgumentException.class,()->GroundHouseLayout.inspect(GroundHouseLayout.MUSHROOM,rotated,ENV));
        var duplicate=new ArrayList<>(data);
        duplicate.add(spawner);
        assertThrows(IllegalArgumentException.class,()->GroundHouseLayout.inspect(GroundHouseLayout.MUSHROOM,duplicate,ENV));
        var wet=new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.WATER,4);
        assertThrows(IllegalArgumentException.class,()->GroundHouseLayout.inspect(GroundHouseLayout.MUSHROOM,data,wet));
        var house=GroundHouseLayout.inspect(GroundHouseLayout.MUSHROOM,data,ENV);
        assertEquals(house.at(6,0,1).move(17,-13,-37),house.move(17,-13,-37).at(6,0,1));
    }

    @Test void originalDefinitionsAndPoolWeightsMatchPinnedRelease() throws Exception {
        assertEquals(HASH,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
        try (var zip=new ZipFile(JAR.toFile())) {
            for (var name:List.of("small_prairie_house","mushroom_house")) {
                var def=json(zip,"data/dungeons_arise/worldgen/structure/"+name+".json");
                assertEquals("minecraft:jigsaw",def.get("type").getAsString());
                assertEquals("WORLD_SURFACE_WG",def.get("project_start_to_heightmap").getAsString());
                assertEquals(0,def.getAsJsonObject("start_height").get("absolute").getAsInt());
                boolean prairie=name.equals("small_prairie_house");
                var directory=(prairie?"prairie/":"fungi/")+name+"/";
                assertEquals("dungeons_arise:"+directory+name+"_start",def.get("start_pool").getAsString());
                var templates=templates(name);
                assertEquals(prairie?4:13,templates.size());
                for (var pool: (prairie?List.of("start","main"):List.of("start","main","spawners"))) {
                    var elements=json(zip,"data/dungeons_arise/worldgen/template_pool/"+directory+name+"_"+pool+".json").getAsJsonArray("elements");
                    var ids=new HashSet<String>();
                    for (var raw:elements) {
                        var entry=raw.getAsJsonObject(); var e=entry.getAsJsonObject("element");
                        String location=e.get("location").getAsString();
                        assertTrue(ids.add(location));
                        assertEquals("minecraft:single_pool_element",e.get("element_type").getAsString());
                        assertEquals("minecraft:empty",e.get("processors").getAsString());
                        assertEquals("rigid",e.get("projection").getAsString());
                        assertEquals(prairie && location.contains("_clean_")?3:1,entry.get("weight").getAsInt());
                    }
                    var expected=new HashSet<String>();
                    for (var template:templates.values()) {
                        String id=template.id();
                        boolean allowed=switch(pool) {
                            case "start" -> id.endsWith("_floor") || id.startsWith("bottom_");
                            case "spawners" -> id.startsWith("piglin_");
                            default -> !id.startsWith("piglin_");
                        };
                        if (allowed) expected.add(template.resource());
                    }
                    assertEquals(expected,ids);
                }
            }
        }
    }
}
