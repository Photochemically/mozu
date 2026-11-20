package dev.expedition.bridge.content;

import com.google.gson.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class NativeDefinitionAdapterTest {
    private static final List<String> ROOMS=List.of("example/purpur_hallway","myth/eden","myth/sewer","myth/timoria","nether/nether_farm","nether/nether_spawners");
    private static final String ROOT="resourcepacks/default/data/dimdoors/pockets/";
    private CompoundTag definition(ZipFile jar,String id) throws Exception {
        String root=id.startsWith("classic/")?ROOT.replace("/default/","/classic/"):ROOT;
        try(var in=jar.getInputStream(Objects.requireNonNull(jar.getEntry(root+"generators/"+id+".json")))) {
            return TagParser.parseTag(new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    private CompoundTag reference(String id) {
        var ref=new CompoundTag();ref.putString("type","dimdoors:id");ref.putString("id",id);return ref;
    }
    @Test void reviewedOriginalsChangeOnlyBuilderAndNeverShareMutableDefinitionState() throws Exception {
        try(var jar=new ZipFile("libs/dimdoors-5.4.4.jar")) {
            for(var id:ROOMS) {
                var original=definition(jar,id);var snapshot=original.copy();var ref=reference(id);var refSnapshot=ref.copy();
                assertFalse(original.contains("builder"));
                var first=NativeDefinitionAdapter.prepare(original,ref,id);var second=NativeDefinitionAdapter.prepare(original,ref,id);
                assertEquals("dimdoors:lazy_gen_pocket",first.getCompound("builder").getString("type"));
                first.remove("builder");assertEquals(snapshot,first,id);
                first.getList("modifiers",Tag.TAG_COMPOUND).getCompound(0).putString("type","changed");
                second.remove("builder");assertEquals(snapshot,second,id);assertEquals(snapshot,original,id);assertEquals(refSnapshot,ref);
            }
        }
    }
    @Test void runtimeSerializationFieldsAndRepeatedPreparationKeepOriginalMeaning() throws Exception {
        try(var jar=new ZipFile("libs/dimdoors-5.4.4.jar")) {
            var id="myth/eden";var tag=definition(jar,id);
            tag.putBoolean("setup_loot",false);tag.putString("placement_type","section_no_update");
            var once=NativeDefinitionAdapter.prepare(tag,reference(id),id);
            assertEquals(once,NativeDefinitionAdapter.prepare(once,reference(id),id));
            once.remove("builder");assertEquals(tag,once);
        }
    }
    @Test void unknownImmediateRoomsExplicitBuildersAndMismatchedReferencesStayRejected() throws Exception {
        try(var jar=new ZipFile("libs/dimdoors-5.4.4.jar")) {
            var id=ROOMS.get(0);var original=definition(jar,id);
            var unknown=original.copy();unknown.putString("id","dungeon/unreviewed");
            assertThrows(IllegalArgumentException.class,()->NativeDefinitionAdapter.prepare(unknown,reference("dungeon/unreviewed"),"dungeon/unreviewed"));
            for(var builder:List.of("{type:'dimdoors:pocket'}","{}","{type:'another:builder'}")) {
                var explicit=original.copy();explicit.put("builder",TagParser.parseTag(builder));
                assertThrows(IllegalArgumentException.class,()->NativeDefinitionAdapter.prepare(explicit,reference(id),id));
            }
            var malformed=original.copy();malformed.putString("builder","invalid");
            assertThrows(IllegalArgumentException.class,()->NativeDefinitionAdapter.prepare(malformed,reference(id),id));
            assertThrows(IllegalArgumentException.class,()->NativeDefinitionAdapter.prepare(original,reference("myth/eden"),id));
            assertThrows(IllegalArgumentException.class,()->NativeDefinitionAdapter.prepare(original,reference(id),"myth/eden"));
        }
    }
    @Test void protectionAndDifferentTemplateTargetsRemainExplicitGaps() throws Exception {
        try(var jar=new ZipFile("libs/dimdoors-5.4.4.jar")) {
            for(var id:List.of("lab/labfinal","classic/ruins/simple_small_t","classic/ruins/small_maze","classic/ruins/transfer_tunnel")) {
                var config=definition(jar,id);var original=config.copy();
                assertThrows(IllegalArgumentException.class,()->NativeDefinitionAdapter.prepare(config,reference(id),id),id);
                assertEquals(original,config);
            }
        }
    }
    @Test void originalAssetDimensionsRiftsAndSpawnerDataAreRecordedWithoutCropping() throws Exception {
        var gson=new GsonBuilder().setPrettyPrinting().create();var rows=new JsonArray();
        var ids=ROOMS;
        int[][] expected={{9,5,5},{1913,34,35},{12,21,1013},{31,31,31},{31,31,31},{31,31,31}};
        int[] riftCounts={8,2,2,3,2,2};
        try(var jar=new ZipFile("libs/dimdoors-5.4.4.jar")) {
            for(int i=0;i<ids.size();i++) {
                String id=ids.get(i),path=ROOT+"schematic/"+id+".schem";byte[] bytes;
                try(var in=jar.getInputStream(Objects.requireNonNull(jar.getEntry(path)))){bytes=in.readAllBytes();}
                CompoundTag asset;try(var in=new java.io.ByteArrayInputStream(bytes)){asset=NbtIo.readCompressed(in);}
                int[] size={asset.getShort("Width"),asset.getShort("Height"),asset.getShort("Length")};assertArrayEquals(expected[i],size);
                assertTrue(asset.getList("Entities",Tag.TAG_COMPOUND).isEmpty());
                var markers=new TreeSet<Integer>();var destinations=new TreeMap<String,Integer>();var devices=new TreeMap<String,Integer>();
                int rifts=0;
                for(var entry:asset.getList("BlockEntities",Tag.TAG_COMPOUND)) {
                    var be=(CompoundTag)entry;String type=be.contains("Id")?be.getString("Id"):be.getString("id");devices.merge(type,1,Integer::sum);
                    int[] pos=be.getIntArray("Pos");assertEquals(3,pos.length);
                    for(int axis=0;axis<3;axis++)assertTrue(pos[axis]>=0&&pos[axis]<size[axis],id+" block entity outside schematic");
                    if(type.equals("dimdoors:entrance_rift")) {
                        rifts++;var target=be.getCompound("data").getCompound("destination");var kind=target.getString("type");
                        destinations.merge(kind,1,Integer::sum);
                        if(kind.equals("dimdoors:id_marker"))assertTrue(markers.add(target.getInt("id")),"Duplicate rift marker");
                    }
                }
                assertEquals(riftCounts[i],rifts,id);
                var declared=new TreeSet<Integer>();
                for(var modifier:definition(jar,id).getList("modifiers",Tag.TAG_COMPOUND)) {
                    var m=(CompoundTag)modifier;
                    if(m.getString("type").equals("dimdoors:rift_data"))
                        for(var number:m.getList("ids",Tag.TAG_INT))declared.add(((NumericTag)number).getAsInt());
                }
                var missing=new TreeSet<>(declared);missing.removeAll(markers);
                assertEquals(id.equals("myth/sewer")?Set.of(2):Set.of(),missing);
                if(id.equals("myth/sewer"))assertEquals(1,destinations.get("dimdoors:global"));
                if(id.equals("nether/nether_spawners"))assertEquals(9,devices.get("minecraft:mob_spawner"));
                else assertFalse(devices.containsKey("minecraft:mob_spawner"));
                var row=new JsonObject();row.addProperty("id",id);row.add("size",gson.toJsonTree(size));row.add("offset",gson.toJsonTree(asset.getIntArray("Offset")));
                row.addProperty("schematicSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
                row.addProperty("entities",0);row.add("blockEntities",gson.toJsonTree(devices));row.add("riftMarkers",gson.toJsonTree(markers));
                row.add("riftTargetTypes",gson.toJsonTree(destinations));row.add("unmatchedDeclaredMarkers",gson.toJsonTree(missing));
                row.addProperty("definitionAdapted",ROOMS.contains(id));rows.add(row);
            }
        }
        var report=new JsonObject();report.addProperty("runtimeTested",false);report.addProperty("automaticPoolEnrollment",false);
        report.addProperty("assetWritesPerformed",false);report.add("rooms",rows);
        Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/native-definition-adapters.json"),gson.toJson(report));
    }
}
