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

/** All eight ORIGINAL sparse cubes and their selected mob attachments; no game startup or native generation. */
class MushroomMineAuditTest {
    private JsonObject json(ZipFile jar,String path) throws Exception {
        try(var in=jar.getInputStream(Objects.requireNonNull(jar.getEntry(path)))){return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
    }
    private CompoundTag template(ZipFile jar,String id) throws Exception {
        try(var in=jar.getInputStream(Objects.requireNonNull(jar.getEntry("data/dungeons_arise/structures/"+id.split(":")[1]+".nbt")))){return NbtIo.readCompressed(in);}
    }
    private void place(CompoundTag n,MushroomMineLayout.Piece piece,Map<Cell,Type> cells) {
        var palette=n.getList("palette",Tag.TAG_COMPOUND);
        for(var raw:n.getList("blocks",Tag.TAG_COMPOUND)) {
            var b=(CompoundTag)raw;var state=palette.getCompound(b.getInt("state"));var props=state.getCompound("Properties");
            var pos=b.getList("pos",Tag.TAG_INT);var p=piece.at(pos.getInt(0),pos.getInt(1),pos.getInt(2));
            String name=state.getString("Name");boolean joint=name.equals("minecraft:jigsaw");if(joint)name=b.getCompound("nbt").getString("final_state");
            if(name.equals("minecraft:structure_void") || name.equals("minecraft:structure_block"))continue;
            cells.put(p,InteriorBlockRules.classify(name,props.getString("type"),props.getString("half"),props.getString("waterlogged").equals("true"),b.contains("nbt")&&!joint));
        }
    }
    @Test void fullSparseBodyKeepsOmittedSpaceAndHasWalkInRouteForEveryRootAndRotation() throws Exception {
        var rows=new JsonArray();int originalBlocks=0,explicitAir=0,mobEntities=0,filledOmissions=0;
        try(var jar=new ZipFile("libs/DungeonsArise-1.20.x-2.1.58-release.jar")) {
            var def=json(jar,"data/dungeons_arise/worldgen/structure/mushroom_mines.json");assertEquals(-8,def.getAsJsonObject("start_height").get("absolute").getAsInt());
            assertEquals("WORLD_SURFACE_WG",def.get("project_start_to_heightmap").getAsString());assertEquals(7,def.get("size").getAsInt());
            var templates=new HashMap<String,CompoundTag>();
            for(String pool:List.of("main","top","spawners"))for(var raw:json(jar,"data/dungeons_arise/worldgen/template_pool/fungi/mushroom_mines/mushroom_mines_"+pool+".json").getAsJsonArray("elements")) {
                var e=raw.getAsJsonObject().getAsJsonObject("element");assertEquals("rigid",e.get("projection").getAsString());assertEquals("minecraft:empty",e.get("processors").getAsString());
                var id=e.get("location").getAsString();templates.put(id,template(jar,id));
            }
            assertEquals(11,templates.size());var mobByJoint=new HashMap<String,String>();
            for(var entry:templates.entrySet()) {
                var n=entry.getValue();var palette=n.getList("palette",Tag.TAG_COMPOUND);
                int part=new MushroomMineLayout.Piece(entry.getKey(),new Cell(0,0,0),0).part();
                if(part>=0) {
                    var size=n.getList("size",Tag.TAG_INT);assertEquals(List.of(32,32,32),List.of(size.getInt(0),size.getInt(1),size.getInt(2)));
                    originalBlocks+=n.getList("blocks",Tag.TAG_COMPOUND).size();
                    for(var e:MushroomMineLayout.joints(part)) {
                        var matches=n.getList("blocks",Tag.TAG_COMPOUND).stream().map(t->(CompoundTag)t).filter(b->{var p=b.getList("pos",Tag.TAG_INT);return p.getInt(0)==e.x()&&p.getInt(1)==e.y()&&p.getInt(2)==e.z();}).toList();
                        assertEquals(1,matches.size());var b=matches.get(0);var s=palette.getCompound(b.getInt("state"));var tag=b.getCompound("nbt");
                        assertEquals("minecraft:jigsaw",s.getString("Name"));assertEquals(e.orientation(),s.getCompound("Properties").getString("orientation"));
                        assertEquals("dungeons_arise:mushroom_mines_"+e.name(),tag.getString("name"));assertEquals("dungeons_arise:mushroom_mines_"+e.target(),tag.getString("target"));
                        assertEquals(e.alignment(),tag.getString("joint"));assertEquals("minecraft:structure_void",tag.getString("final_state"));
                        assertEquals("dungeons_arise:fungi/mushroom_mines/mushroom_mines_"+e.pool(),tag.getString("pool"));
                    }
                }
                for(var raw:n.getList("blocks",Tag.TAG_COMPOUND)){var b=(CompoundTag)raw;String name=palette.getCompound(b.getInt("state")).getString("Name");
                    if(part>=0 && name.equals("minecraft:air"))explicitAir++;
                    if(part<0 && name.equals("minecraft:jigsaw"))mobByJoint.put(b.getCompound("nbt").getString("name"),entry.getKey());}
                if(part<0)mobEntities+=n.getList("entities",Tag.TAG_COMPOUND).size();
            }
            assertEquals(3,mobByJoint.size());
            int[][] offsets={{0,0,0},{32,0,0},{32,0,32},{0,0,32},{0,32,0},{32,32,0},{32,32,32},{0,32,32}};
            for(int rotation=0;rotation<4;rotation++)for(int root=0;root<4;root++) {
                var origin=new MushroomMineLayout.Piece("",new Cell(-512,87,-300),rotation);var pieces=new ArrayList<MushroomMineLayout.Piece>();
                var cells=new HashMap<Cell,Type>();BlockBounds body=null;
                for(int i=0;i<8;i++){int[] o=offsets[i];var p=new MushroomMineLayout.Piece(MushroomMineLayout.PREFIX+"part_"+i,origin.at(o[0],o[1],o[2]),rotation);pieces.add(p);body=body==null?p.bounds():IslandPlan.union(body,p.bounds());place(templates.get(p.template()),p,cells);}
                int attachments=0;
                for(var p:List.copyOf(pieces)) {
                    var n=templates.get(p.template());var palette=n.getList("palette",Tag.TAG_COMPOUND);
                    for(var raw:n.getList("blocks",Tag.TAG_COMPOUND)) {
                        var b=(CompoundTag)raw;if(!palette.getCompound(b.getInt("state")).getString("Name").equals("minecraft:jigsaw"))continue;
                        var id=mobByJoint.get(b.getCompound("nbt").getString("target"));if(id==null)continue;
                        var q=b.getList("pos",Tag.TAG_INT);var mob=new MushroomMineLayout.Piece(id,p.at(q.getInt(0),q.getInt(1)+1,q.getInt(2)),rotation);
                        pieces.add(mob);place(templates.get(id),mob,cells);attachments++;
                    }
                }
                Collections.swap(pieces,0,root);var layout=MushroomMineLayout.fromPieces(pieces,body);
                var island=new IslandPlan(body,layout.groundY(),8,6,24,IslandPlan.Surface.GRASS);assertEquals(120,ReservationGeometry.plan(island.completeBounds(),16,0,256).padded().height());
                var before=new HashMap<>(cells);var terrain=new IslandCursor(island);int terrainWrites=0;
                while(terrain.hasNext()){var c=terrain.next();assertFalse(body.contains(c.x(),c.y(),c.z()));terrainWrites++;}
                InteriorSiteSearch.Probe probe=p->{var c=island.solidColumn(p.x(),p.z());return cells.getOrDefault(p,c!=null&&p.y()>=c.bottom()&&p.y()<=c.top()?Type.SUPPORT:Type.AIR);};
                var search=new MushroomMineSiteSearch(layout,probe);while(!search.complete())search.step(31);assertTrue(search.result().isPresent(),"root "+root+", rotation "+rotation);
                assertEquals(before,cells);var arrival=search.result().orElseThrow();assertEquals(layout.groundY(),arrival.y());assertFalse(body.contains(arrival.x(),arrival.y(),arrival.z()));
                assertTrue(InteriorSiteSearch.clearPlatform(arrival,probe));assertEquals(layout.platform(),search.route().get(search.route().size()-1));
                for(int i=1;i<search.route().size();i++){var p=search.route().get(i-1);var q=search.route().get(i);assertEquals(1,Math.abs(p.x()-q.x())+Math.abs(p.z()-q.z()));assertTrue(Math.abs(p.y()-q.y())<=1);}
                cells.put(layout.platform().move(0,1,0),Type.BLOCKED);var blocked=new MushroomMineSiteSearch(layout,probe);while(!blocked.complete())blocked.step(256);assertTrue(blocked.result().isEmpty());
                if(rotation==0&&root==0)for(int x=body.minX();x<=body.maxX();x++)for(int z=body.minZ();z<=body.maxZ();z++)for(int y=87;y<96;y++)if(!before.containsKey(new Cell(x,y,z)))filledOmissions++;
                var row=new JsonObject();row.addProperty("rootPart",root);row.addProperty("rotation",rotation);row.addProperty("originalMobAttachments",attachments);row.addProperty("terrainWrites",terrainWrites);row.addProperty("routeCells",search.route().size());row.add("arrival",new Gson().toJsonTree(arrival));rows.add(row);
            }
        }
        var report=new JsonObject();report.add("cases",rows);report.addProperty("originalMainBlockEntries",originalBlocks);report.addProperty("explicitMainAir",explicitAir);report.addProperty("mobTemplateEntities",mobEntities);report.addProperty("omittedCellsFilledAtUnadaptedSurface",filledOmissions);
        assertEquals(16,rows.size());assertEquals(63,explicitAir);assertTrue(filledOmissions>20000);assertTrue(mobEntities>0);
        report.addProperty("jarSha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of("libs/DungeonsArise-1.20.x-2.1.58-release.jar")))));
        assertEquals("f8f1254dbe7ed7c1d5403f20361a36f0d0587c82b71313e3f427c7f1ce7c8a90",report.get("jarSha256").getAsString());
        report.addProperty("runtimeTested",false);report.addProperty("scope","16 full eight-cube original NBT compositions with mob attachment block effects; no native random generation, entity spawn, player collision, extra exits, multiplayer or restart acceptance");
        Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/mushroom-mine-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
