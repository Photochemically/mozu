package dev.expedition.bridge.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.resources.IoSupplier;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Virtual lowest-priority data resources; higher-priority user packs can supply explicit repairs. */
public final class WdaAliasPack extends AbstractPackResources {
    private static final byte[] META="{\"pack\":{\"pack_format\":15,\"description\":\"Expedition: WDA resource repairs and approved substitutes\"}}".getBytes(StandardCharsets.UTF_8);
    private final Map<String,byte[]> aliases;
    public WdaAliasPack(String id,Map<String,byte[]> aliases) {super(id,true);this.aliases=aliases;}
    @Override public IoSupplier<InputStream> getRootResource(String... parts) {
        return parts.length==1 && parts[0].equals("pack.mcmeta")?()->new ByteArrayInputStream(META):null;
    }
    @Override public IoSupplier<InputStream> getResource(PackType type,ResourceLocation location) {
        if(type!=PackType.SERVER_DATA || !location.getNamespace().equals("dungeons_arise"))return null;
        var bytes=aliases.get(location.getPath());return bytes==null?null:()->new ByteArrayInputStream(bytes);
    }
    @Override public void listResources(PackType type,String namespace,String prefix,ResourceOutput output) {
        if(type!=PackType.SERVER_DATA || !namespace.equals("dungeons_arise"))return;
        aliases.forEach((path,bytes)->{
            if(prefix.isEmpty() || path.startsWith(prefix.endsWith("/")?prefix:prefix+"/"))
                output.accept(ResourceLocation.fromNamespaceAndPath(namespace,path),()->new ByteArrayInputStream(bytes));
        });
    }
    @Override public Set<String> getNamespaces(PackType type) {return type==PackType.SERVER_DATA?Set.of("dungeons_arise"):Set.of();}
    @Override public void close() {}
}
