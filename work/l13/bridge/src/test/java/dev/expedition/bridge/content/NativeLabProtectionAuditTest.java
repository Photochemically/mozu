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

/** Source survey, not a complete or enabled lab protection profile. Never includes original books/sign text. */
class NativeLabProtectionAuditTest {
    private record Position(int x,int y,int z){}
    @Test void pinnedLabKeepsItsMovingMachineEntitiesAndUnmodifiedProtectionUntilAnAdapterExists() throws Exception {
        String prefix="resourcepacks/default/data/dimdoors/pockets/";CompoundTag source,definition;byte[] bytes;
        try(var jar=new ZipFile("libs/dimdoors-5.4.4.jar")) {
            try(var in=jar.getInputStream(jar.getEntry(prefix+"schematic/lab/labfinal.schem"))){bytes=in.readAllBytes();}
            try(var in=new java.io.ByteArrayInputStream(bytes)){source=NbtIo.readCompressed(in);}
            try(var in=jar.getInputStream(jar.getEntry(prefix+"generators/lab/labfinal.json"))) {
                definition=TagParser.parseTag(new String(in.readAllBytes(),StandardCharsets.UTF_8));
            }
        }
        String fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        assertEquals("ae9b9cb6fad915cef3430779e5e4049cbb0b8e16d6a8e0842e3da478ecd93d8d",fingerprint);
        assertEquals(24,source.getShort("Width"));assertEquals(108,source.getShort("Height"));assertEquals(88,source.getShort("Length"));
        var palette=new HashMap<Integer,String>();var raw=source.getCompound("Palette");
        for(var state:raw.getAllKeys())palette.put(raw.getInt(state),state);
        var cells=new HashMap<Position,String>();int index=0,value=0,shift=0;
        for(byte rawByte:source.getByteArray("BlockData")) {
            int b=Byte.toUnsignedInt(rawByte);value|=(b&127)<<shift;
            if((b&128)!=0){shift+=7;assertTrue(shift<32);continue;}
            String state=Objects.requireNonNull(palette.get(value));
            cells.put(new Position(index%24,index/(24*88),index/24%88),state);index++;value=0;shift=0;
        }
        assertEquals(0,shift);assertEquals(24*108*88,index);
        var types=Set.of("minecraft:redstone_wire","minecraft:sticky_piston","minecraft:piston_head","minecraft:slime_block","minecraft:repeater","minecraft:lever");
        var components=new TreeMap<String,Integer>();var candidates=new HashSet<Position>();var hardware=new JsonArray();
        cells.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<Position,String> e)->e.getKey().y())
                .thenComparingInt(e->e.getKey().z()).thenComparingInt(e->e.getKey().x())).forEach(entry->{
            String state=entry.getValue(),type=state.split("\\[",2)[0];if(!types.contains(type))return;
            var p=entry.getKey();components.merge(type,1,Integer::sum);candidates.add(p);
            var below=new Position(p.x(),p.y()-1,p.z());if(!"minecraft:air".equals(cells.get(below)))candidates.add(below);
            var row=new JsonObject();row.add("position",new Gson().toJsonTree(p));row.addProperty("state",state);hardware.add(row);
        });
        assertEquals(Map.of("minecraft:redstone_wire",17,"minecraft:sticky_piston",2,"minecraft:piston_head",2,
                "minecraft:slime_block",1,"minecraft:repeater",2,"minecraft:lever",1),components);
        assertEquals(46,candidates.size());
        var entityTypes=new TreeMap<String,Integer>();for(var item:source.getList("Entities",Tag.TAG_COMPOUND)) {
            var e=(CompoundTag)item;entityTypes.merge(e.getString("Id"),1,Integer::sum);
        }
        assertEquals(Map.of("minecraft:skeleton",25,"minecraft:painting",1,"minecraft:armor_stand",6),entityTypes);
        var ref=new CompoundTag();ref.putString("type","dimdoors:id");ref.putString("id","lab/labfinal");var before=definition.copy();
        assertThrows(IllegalArgumentException.class,()->NativeDefinitionAdapter.prepare(definition,ref,"lab/labfinal"));assertEquals(before,definition);
        var report=new JsonObject();report.addProperty("runtimeTested",false);report.addProperty("adapterEnabled",false);
        report.addProperty("sourceSchematicSha256",fingerprint);report.add("size",new Gson().toJsonTree(List.of(24,108,88)));
        report.add("machineComponents",hardware);report.addProperty("componentCount",25);report.addProperty("componentsAndNonAirBelowCount",46);
        report.addProperty("candidateCellsAreNotFinalProtectionProfile",true);report.add("originalEntityTypes",new Gson().toJsonTree(entityTypes));
        report.addProperty("remaining","Trusted native piston motion, slime adhesion, complete functional-device scope, indirect writes and live entity/route behavior require verification; whole-room addon remains unchanged and refused.");
        Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/native-lab-protection-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
