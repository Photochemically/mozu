package dev.expedition.bridge.content.wda;

import com.google.gson.*;
import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

/** Composes unchanged original NBT, not a Minecraft generator or collision simulation. */
class BlimpLadderAuditTest {
    private record State(Type type,Cell ladderFacing){}
    private JsonObject json(ZipFile jar,String path) throws Exception {
        try(var in=jar.getInputStream(Objects.requireNonNull(jar.getEntry(path)))){return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
    }
    private CompoundTag template(ZipFile jar,String id) throws Exception {
        try(var in=jar.getInputStream(Objects.requireNonNull(jar.getEntry("data/dungeons_arise/structures/"+id.split(":")[1]+".nbt")))){return NbtIo.readCompressed(in);}
    }
    private BlockBounds place(CompoundTag n,BlimpLadderLayout.Piece piece,Map<Cell,State> cells) {
        var palette=n.getList("palette",Tag.TAG_COMPOUND);var size=n.getList("size",Tag.TAG_INT);
        for(var raw:n.getList("blocks",Tag.TAG_COMPOUND)) {
            var b=(CompoundTag)raw;var state=palette.getCompound(b.getInt("state"));var props=state.getCompound("Properties");
            var pos=b.getList("pos",Tag.TAG_INT);var p=piece.at(pos.getInt(0),pos.getInt(1),pos.getInt(2));
            String name=state.getString("Name");boolean joint=name.equals("minecraft:jigsaw");
            if(joint)name=b.getCompound("nbt").getString("final_state");
            if(name.equals("minecraft:structure_void") || name.equals("minecraft:structure_block"))continue;
            var kind=InteriorBlockRules.classify(name,props.getString("type"),props.getString("half"),props.getString("waterlogged").equals("true"),b.contains("nbt")&&!joint);
            Cell face=null;
            if(name.equals("minecraft:ladder")) {
                var local=switch(props.getString("facing")){case "north"->new Cell(0,0,-1);case "south"->new Cell(0,0,1);case "east"->new Cell(1,0,0);case "west"->new Cell(-1,0,0);default->throw new AssertionError();};
                var rotated=piece.at(local.x(),0,local.z());face=new Cell(rotated.x()-piece.origin().x(),0,rotated.z()-piece.origin().z());
                assertFalse(props.getString("waterlogged").equals("true"));
            }
            cells.put(p,new State(kind,face));
        }
        var a=piece.at(0,0,0);var b=piece.at(size.getInt(0)-1,size.getInt(1)-1,size.getInt(2)-1);
        return new BlockBounds(Math.min(a.x(),b.x()),a.y(),Math.min(a.z(),b.z()),Math.max(a.x(),b.x()),b.y(),Math.max(a.z(),b.z()));
    }
    private void joint(CompoundTag template,int x,int y,int z,String orientation,String name,String target) {
        var palette=template.getList("palette",Tag.TAG_COMPOUND);
        var matches=template.getList("blocks",Tag.TAG_COMPOUND).stream().map(t->(CompoundTag)t).filter(b->{var p=b.getList("pos",Tag.TAG_INT);return p.getInt(0)==x&&p.getInt(1)==y&&p.getInt(2)==z;}).toList();
        assertEquals(1,matches.size());var b=matches.get(0);var state=palette.getCompound(b.getInt("state"));var n=b.getCompound("nbt");
        assertEquals("minecraft:jigsaw",state.getString("Name"));assertEquals(orientation,state.getCompound("Properties").getString("orientation"));
        assertEquals("dungeons_arise:"+name,n.getString("name"));assertEquals("dungeons_arise:"+target,n.getString("target"));
        assertEquals("aligned",n.getString("joint"));assertEquals("minecraft:air",n.getString("final_state"));
        assertEquals("dungeons_arise:enigmatic/small_blimp/small_blimp_main",n.getString("pool"));
    }
    @Test void originalLaddersBothHullsAndDoorLandingWorkAtAllRotations() throws Exception {
        var rows=new JsonArray();
        try(var jar=new ZipFile("libs/DungeonsArise-1.20.x-2.1.58-release.jar")) {
            var definition=json(jar,"data/dungeons_arise/worldgen/structure/small_blimp.json");
            assertEquals("WORLD_SURFACE_WG",definition.get("project_start_to_heightmap").getAsString());assertEquals(100,definition.getAsJsonObject("start_height").get("absolute").getAsInt());
            assertEquals(7,definition.get("size").getAsInt());assertEquals(114,definition.get("max_distance_from_center").getAsInt());
            var resources=new HashMap<String,CompoundTag>();
            for(String name:List.of("small_blimp_start","small_blimp_main","small_blimp_spawners")) {
                var pool=json(jar,"data/dungeons_arise/worldgen/template_pool/enigmatic/small_blimp/"+name+".json");
                for(var raw:pool.getAsJsonArray("elements")) {
                    var e=raw.getAsJsonObject().getAsJsonObject("element");assertEquals("rigid",e.get("projection").getAsString());assertEquals("minecraft:empty",e.get("processors").getAsString());
                    var id=e.get("location").getAsString();resources.put(id,template(jar,id));
                }
            }
            assertEquals(6,resources.size());
            joint(resources.get(BlimpLadderLayout.LADDER),1,0,0,"down_north","ladders","ladders");
            joint(resources.get(BlimpLadderLayout.LADDER),1,47,0,"up_north","ladders","ladders");
            joint(resources.get(BlimpLadderLayout.HULL0),28,0,11,"down_north","ladders","ladders");
            joint(resources.get(BlimpLadderLayout.HULL0),30,0,0,"east_up","blimp_side_1","blimp_side_0");
            joint(resources.get(BlimpLadderLayout.HULL1),0,0,0,"west_up","blimp_side_0","blimp_side_1");
            for(int count=1;count<=3;count++)for(int rotation=0;rotation<4;rotation++) {
                var transform=new BlimpLadderLayout.Piece(BlimpLadderLayout.LADDER,new Cell(-200,96+99-(count-1)*48,300),rotation);
                var pieces=new ArrayList<BlimpLadderLayout.Piece>();
                // The native root can be above subsequently generated lower ladder segments.
                for(int segment=count-1;segment>=0;segment--)pieces.add(new BlimpLadderLayout.Piece(BlimpLadderLayout.LADDER,transform.at(0,segment*48,0),rotation));
                pieces.add(new BlimpLadderLayout.Piece(BlimpLadderLayout.HULL0,transform.at(-27,count*48,-11),rotation));
                pieces.add(new BlimpLadderLayout.Piece(BlimpLadderLayout.HULL1,transform.at(4,count*48,-11),rotation));
                var cells=new HashMap<Cell,State>();BlockBounds bounds=null;
                for(var p:pieces){var b=place(resources.get(p.template()),p,cells);bounds=bounds==null?b:IslandPlan.union(bounds,b);}
                var layout=BlimpLadderLayout.fromPieces(pieces,bounds);var island=new IslandPlan(bounds,layout.foot().y(),8,6,24,IslandPlan.Surface.GRASS);
                assertDoesNotThrow(()->ReservationGeometry.plan(island.completeBounds(),16,0,256));
                var before=new HashMap<>(cells);
                var probe=new BlimpLadderSiteSearch.Probe() {
                    public Type at(Cell p) {
                        var s=cells.get(p);if(s!=null)return s.type();var c=island.solidColumn(p.x(),p.z());
                        return c!=null && p.y()>=c.bottom() && p.y()<=c.top()?Type.SUPPORT:Type.AIR;
                    }
                    public boolean ladder(Cell p,int dx,int dz){var s=cells.get(p);return s!=null && new Cell(dx,0,dz).equals(s.ladderFacing());}
                };
                var search=new BlimpLadderSiteSearch(layout,probe);while(!search.complete())search.step(7);
                assertEquals(Optional.of(layout.arrival()),search.result());assertEquals(before,cells);
                assertEquals(Type.BLOCKED,probe.at(layout.landing().move(-layout.dx(),0,-layout.dz())),"Original iron door must remain in the route");
                assertEquals(count*48+5,layout.top().y()-layout.foot().y()+1);
                cells.remove(layout.foot().move(0,20,0));var broken=new BlimpLadderSiteSearch(layout,probe);while(!broken.complete())broken.step(256);assertTrue(broken.result().isEmpty());
                var row=new JsonObject();row.addProperty("rotation",rotation);row.addProperty("ladderSegments",count);row.addProperty("climbBlocks",count*48+5);
                row.addProperty("requiredHeight",island.completeBounds().expand(16).height());row.add("arrival",new Gson().toJsonTree(layout.arrival()));rows.add(row);
            }
        }
        var report=new JsonObject();report.add("cases",rows);assertEquals(12,rows.size());
        report.addProperty("jarSha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of("libs/DungeonsArise-1.20.x-2.1.58-release.jar")))));
        assertEquals("f8f1254dbe7ed7c1d5403f20361a36f0d0587c82b71313e3f427c7f1ce7c8a90",report.get("jarSha256").getAsString());
        report.addProperty("runtimeTested",false);report.addProperty("scope","12 composed original ladder + both hull cases; native generator, floating-ladder stability, player collision, live doors, spawner placement, extra exits and restart remain untested");
        Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/blimp-ladder-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
