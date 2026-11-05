package dev.expedition.bridge.compat;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.Function;

/** WDA 2.1.58 repairs and explicitly approved substitutes, read from installed assets, never bundled NBT. */
public final class WdaAliasData {
    public record Alias(String missing,String source,boolean pool){}
    public record Substitute(String missing,String source,String sourceSha256,String pool,String poolSha256){}
    public static final List<Alias> ALIASES=List.of(
            new Alias("worldgen/template_pool/mechanical_nest/mechanical_nest_decoration.json",
                    "worldgen/template_pool/mechanical_nest/mechanical_nest_decorations.json",true),
            new Alias("structures/mechanical_nest/mechanical_nest_bridge_terminator_6_.nbt",
                    "structures/mechanical_nest/mechanical_nest_bridge_terminator_6.nbt",false),
            new Alias("structures/thornborn_towers/thornborn_towers_hanging_bridge_2_medium_terminator.nbt",
                    "structures/thornborn_towers/thornborn_towers_hanging_bridge_3_medium_terminator.nbt",false),
            new Alias("worldgen/template_pool/underworld/foundry/foundry_corridor_gears.json",
                    "worldgen/template_pool/underworld/foundry/foundry_corridor_deco.json",true));
    // These are compatibility choices, NOT recovered originals. Keep the original pools/weights unchanged.
    // Fingerprints pin the reviewed geometry, connector, complete mob NBT and owning pool semantics.
    public static final List<Substitute> SUBSTITUTIONS=List.of(
            new Substitute("structures/bandit_village/bandit_village_deco_3.nbt",
                    "structures/bandit_village/bandit_village_deco_0.nbt",
                    "61295310d71474a442e83ba03a803713e1ba4a2318c9271eca8c4951c8208a3e",
                    "worldgen/template_pool/bandit/bandit_village/bandit_village_houses.json",
                    "29a71070756060a65c0531676cfe9efb8da5bb5c03e79d4b6846eb7cdb940643"),
            new Substitute("structures/mechanical_nest/mechanical_nest_spawner_6.nbt",
                    "structures/mechanical_nest/mechanical_nest_spawner_0.nbt",
                    "cbc852ac1e1d9008ea490e0b8980bdeb447782ca3e2136ab23a57ca871da739a",
                    "worldgen/template_pool/mechanical_nest/mechanical_nest_spawners.json",
                    "5d0b4d05ebdad6ee224ba4553af61157342b319b5a7069e2bc1c1996dc75727c"));
    private WdaAliasData(){}
    public static Map<String,byte[]> build(Function<String,byte[]> readInstalledResource) {
        Map<String,byte[]> result=new LinkedHashMap<>();
        for(var alias:ALIASES) {
            // An upstream release or locally repaired mod containing the exact name takes precedence.
            if(readInstalledResource.apply(alias.missing())!=null)continue;
            var bytes=readInstalledResource.apply(alias.source());
            if(bytes==null || bytes.length==0)throw new IllegalArgumentException("Missing audited WDA alias source: "+alias.source());
            if(alias.pool()) {
                var pool=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
                String sourceId=poolId(alias.source());
                if(!pool.has("name") || !pool.get("name").getAsString().equals(sourceId)
                        || !pool.has("elements") || pool.getAsJsonArray("elements").isEmpty())
                    throw new IllegalArgumentException("Unexpected WDA source pool: "+alias.source());
                pool.addProperty("name",poolId(alias.missing()));
                result.put(alias.missing(),pool.toString().getBytes(StandardCharsets.UTF_8));
            } else result.put(alias.missing(),bytes.clone());
        }
        for(var substitute:SUBSTITUTIONS) {
            if(readInstalledResource.apply(substitute.missing())!=null)continue;
            var bytes=readInstalledResource.apply(substitute.source());
            requireFingerprint(bytes,substitute.sourceSha256(),substitute.source());
            requireFingerprint(readInstalledResource.apply(substitute.pool()),substitute.poolSha256(),substitute.pool());
            result.put(substitute.missing(),bytes.clone());
        }
        return Collections.unmodifiableMap(result);
    }
    private static void requireFingerprint(byte[] bytes,String expected,String resource) {
        if(bytes==null || bytes.length==0)throw new IllegalArgumentException("Missing WDA substitute source: "+resource);
        try {
            if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(expected))
                throw new IllegalArgumentException("WDA substitute source changed; review required: "+resource);
        } catch(NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}
    }
    private static String poolId(String path) {
        return "dungeons_arise:"+path.substring("worldgen/template_pool/".length(),path.length()-".json".length());
    }
}
