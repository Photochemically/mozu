package dev.expedition.bridge.content.wda;

import com.google.gson.GsonBuilder;
import dev.expedition.bridge.domain.IslandPlan;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import static dev.expedition.bridge.content.wda.CampTemplateFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class IllagerCampAuditTest {
    private static final WdaStructurePlanner.Environment ENV=new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.GRASS);
    private static final List<String> ENEMIES=List.of("vindicator_normal_0","pillager_normal_0","pillager_multishot_0");
    private static final List<String> TENTS=List.of("tent_0","tent_1","tent_2","tent_2","tent_3");
    private static final List<Direction> TENT_PORTS=List.of(Direction.EAST,Direction.SOUTH,Direction.WEST,Direction.EAST,Direction.SOUTH);

    private static List<Placed> assemble(Map<String,Template> templates,int road,int tent,Rotation rot) {
        var center=new Placed(templates.get("street_6_main"),new BlockPos(-75,Y-1,219),rot);
        var street=connect(center,center.template().joint("campsite_street",Direction.EAST),templates.get("street_"+road),
                road==1?Direction.NORTH:Direction.WEST);
        var pieces=new ArrayList<>(List.of(center,street));
        int sample=rot.ordinal()+road+tent;
        if (road<4) {
            var port=street.template().joint("campsite_tent",road<2?Direction.SOUTH:Direction.NORTH);
            var house=connect(street,port,templates.get(TENTS.get(tent)),TENT_PORTS.get(tent));
            pieces.add(house);
            pieces.add(connect(house,house.template().joint("campsite_illager",Direction.UP),
                    templates.get(ENEMIES.get((sample+1)%3)),Direction.DOWN));
            if (road==3) {
                int other=(tent+1)%TENTS.size();
                pieces.add(connect(street,street.template().joint("campsite_tent",Direction.EAST),templates.get(TENTS.get(other)),TENT_PORTS.get(other)));
            }
        }
        if (road!=5) pieces.add(connect(street,street.template().joint("campsite_deco",Direction.UP),
                templates.get("deco_"+sample%7),Direction.DOWN));
        pieces.add(connect(street,street.template().joint("campsite_illager",Direction.UP),
                templates.get(ENEMIES.get(sample%3)),Direction.DOWN));
        return pieces;
    }
    private static class Model extends Terrain implements IllagerCampLayout.Probe {
        final IllagerCampLayout camp;
        Model(List<Placed> pieces) {
            super(pieces);
            camp=IllagerCampLayout.inspect(pieces.stream().map(Placed::piece).toList(),ENV);
        }
        boolean has(Cell p,String id) { return !blocked.contains(p) && placed.containsKey(p) && placed.get(p).id().equals("minecraft:"+id); }
        public boolean chest(Cell p) { return has(p,"chest"); }
        public boolean dirtPath(Cell p) { return has(p,"dirt_path"); }
        public boolean signalFire(Cell p) {
            if (!has(p,"campfire")) return false;
            var props=placed.get(p).props();
            return props.getString("lit").equals("true") && props.getString("signal_fire").equals("true")
                    && props.getString("waterlogged").equals("false");
        }
        void route(int exits) {
            var search=camp.survey(this,island,exits);
            for(int i=0;i<20_000;i++) if(search.getAsBoolean()) return;
            fail("Camp survey exceeded island footprint");
        }
    }

    @Test void nativeStreetTentAndEncounterCombinationsPreserveFireRingAndAccess() throws Exception {
        var templates=templates("illager_campsite");
        var cases=new ArrayList<Map<String,Object>>();
        Set<String> exercised=new HashSet<>();
        for(var rot:Rotation.values()) for(int road=0;road<6;road++) for(int tent=0;tent<(road<4?5:1);tent++) {
            var pieces=assemble(templates,road,tent,rot);
            var model=new Model(pieces);
            for(int count=1;count<=4;count++) model.route(count);
            var chest=model.camp.mapChest();
            assertEquals("dungeons_arise:chests/illager_campsite/illager_campsite_map",model.placed.get(chest).data().getString("LootTable"));
            long loot=pieces.stream().flatMap(p->p.template().blocks().stream()).filter(b->b.data().contains("LootTable")).count();
            assertEquals(loot,model.placed.values().stream().filter(s->s.data().contains("LootTable")).count());
            var ids=pieces.stream().map(p->p.template().id()).toList();
            exercised.addAll(ids);
            var row=new LinkedHashMap<String,Object>();
            row.put("rotation",rot.toString());row.put("pieces",ids);row.put("tentPort",road<4?TENT_PORTS.get(tent).getName():"none");
            row.put("tentInteriors",model.camp.tentInteriors().size());row.put("lootContainers",loot);row.put("exitCounts",List.of(1,2,3,4));cases.add(row);
        }
        assertEquals(templates.keySet(),exercised);
        var report=new LinkedHashMap<String,Object>();
        report.put("wdaSha256",HASH);report.put("cases",cases);report.put("runtimeAccepted",false);
        report.put("scope","Original connected template samples and encounter NBT. Routes reach each placed tent and the outside of the four lit signal fires. No world simulation, random generation rates, combat or chest-opening claim.");
        var f=Path.of("build/reports/illager-camp-entry-audit.json");Files.createDirectories(f.getParent());
        Files.writeString(f,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
    }

    @Test void refusesBrokenHazardsDisconnectedCampCenterAndBlockedSecondaryTent() throws Exception {
        var m=new Model(assemble(templates("illager_campsite"),3,2,Rotation.CLOCKWISE_90));
        var chest=m.camp.mapChest();
        assertEquals(2,m.camp.tentInteriors().size());
        for(var p:List.of(chest,chest.move(-1,0,0),chest.move(0,1,0),m.camp.tentInteriors().get(1))) {
            m.blocked.add(p);
            assertThrows(IllegalStateException.class,()->m.route(2));
            m.blocked.remove(p);
        }
        for(var d:Direction.Plane.HORIZONTAL) m.blocked.add(chest.move(d.getStepX()*3,0,d.getStepZ()*3));
        assertThrows(IllegalStateException.class,()->m.route(1));
        m.blocked.clear();
        var exit=m.island.perimeterSites(2).get(1);
        m.blocked.add(exit);
        assertThrows(IllegalStateException.class,()->m.route(2));
    }

    @Test void walksAcrossDirtPathsWithoutInstallingARequiredDoorOnTheLowerSurface() throws Exception {
        var m=new Model(assemble(templates("illager_campsite"),0,1,Rotation.NONE));
        var exit=m.island.perimeterSites(1).get(0);
        var path=m.placed.values().stream().filter(s->s.id().equals("minecraft:dirt_path")).findFirst().orElseThrow();
        // Surround the clear 3x3 exit platform, so there is no alternate route on full grass blocks.
        for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))==2) m.placed.put(exit.move(dx,-1,dz),path);
        }
        m.route(1);
        m.placed.put(exit.move(0,-1,0),path);
        assertThrows(IllegalStateException.class,()->m.route(1));
    }

    @Test void acceptsNativePatrolStreetsAndRejectsDetachedOrMismatchedPieces() throws Exception {
        var samples=templates("illager_campsite");
        var patrol=assemble(samples,5,0,Rotation.NONE);
        var m=new Model(patrol);assertTrue(m.camp.tentInteriors().isEmpty());m.route(1);
        var full=assemble(samples,1,4,Rotation.CLOCKWISE_180);
        var data=full.stream().map(Placed::piece).toList(); var base=data.get(0); var street=data.get(1); var tent=data.get(2);
        assertThrows(IllegalArgumentException.class,()->IllagerCampLayout.inspect(List.of(base),ENV));
        assertThrows(IllegalArgumentException.class,()->IllagerCampLayout.inspect(List.of(base,street,base),ENV));
        for(var bad:List.of(new WdaTemplatePiece(tent.id(),tent.origin().above(),tent.rotation(),tent.bounds().move(0,1,0),false),
                new WdaTemplatePiece(street.id(),street.origin(),street.rotation(),street.bounds(),false)))
            assertThrows(IllegalArgumentException.class,()->IllagerCampLayout.inspect(List.of(base,street,bad),ENV));
        var camp=IllagerCampLayout.inspect(data,ENV);var relocated=camp.move(244,-18,-236);
        assertEquals(camp.mapChest().move(244,-18,-236),relocated.mapChest());
        assertEquals(camp.tentInteriors().stream().map(p->p.move(244,-18,-236)).toList(),relocated.tentInteriors());
    }

    @Test void originalPoolWeightsAndEncounterEntitiesMatchPinnedRelease() throws Exception {
        assertEquals(HASH,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
        var samples=templates("illager_campsite");
        assertEquals(21,samples.size());
        for(var id:ENEMIES) {
            var entities=samples.get(id).entities();assertEquals(1,entities.size());
            assertEquals(id.startsWith("pillager")?"minecraft:pillager":"minecraft:vindicator",entities.getCompound(0).getCompound("nbt").getString("id"));
        }
        assertEquals("minecraft:zombie_villager",samples.get("deco_0").entities().getCompound(0).getCompound("nbt").getString("id"));
        var hand=samples.get("pillager_multishot_0").entities().getCompound(0).getCompound("nbt").getList("HandItems",Tag.TAG_COMPOUND).getCompound(0);
        assertEquals("minecraft:crossbow",hand.getString("id"));
        var enchants=hand.getCompound("tag").getList("Enchantments",Tag.TAG_COMPOUND);var levels=new HashMap<String,Integer>();
        for(int i=0;i<enchants.size();i++)levels.put(enchants.getCompound(i).getString("id"),enchants.getCompound(i).getInt("lvl"));
        assertEquals(Map.of("minecraft:multishot",1,"minecraft:quick_charge",2),levels);
        try(var zip=new ZipFile(JAR.toFile())) {
            String path="data/dungeons_arise/worldgen/";
            var def=json(zip,path+"structure/illager_campsite.json");
            assertEquals("minecraft:jigsaw",def.get("type").getAsString());assertEquals("WORLD_SURFACE_WG",def.get("project_start_to_heightmap").getAsString());
            assertEquals(0,def.getAsJsonObject("start_height").get("absolute").getAsInt());
            assertEquals("dungeons_arise:campsites/illager_campsite/illager_campsite_start",def.get("start_pool").getAsString());
            Set<String> ids=new HashSet<>();
            for(String pool:List.of("illager_campsite_start","illager_campsite_streets","illager_campsite_tents","illager_campsite_deco","enemies")) {
                for(var raw:json(zip,path+"template_pool/campsites/illager_campsite/"+pool+".json").getAsJsonArray("elements")){
                    var entry=raw.getAsJsonObject();var e=entry.getAsJsonObject("element");
                    String location=e.get("location").getAsString();
                    assertTrue(location.startsWith(IllagerCampLayout.PREFIX));
                    String id=location.substring(IllagerCampLayout.PREFIX.length());ids.add(id);
                    assertEquals(id.startsWith("street_")?"terrain_matching":"rigid",e.get("projection").getAsString());
                    assertEquals("minecraft:single_pool_element",e.get("element_type").getAsString());assertEquals("minecraft:empty",e.get("processors").getAsString());
                    int weight=switch(id){case "street_5"->5;case "pillager_normal_0"->3;case "pillager_multishot_0"->2;default->1;};
                    assertEquals(weight,entry.get("weight").getAsInt());
                }
            }
            assertEquals(samples.keySet(),ids);
        }
    }
}
