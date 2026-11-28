package dev.expedition.bridge.mixin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pins the vanilla silent-removal surface that the guard closes, without bootstrapping a Minecraft world. */
class SilentRemovalHookTest {
    private ClassNode read(String name) throws Exception {
        var node=new ClassNode();
        try(var in=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(name+".class"))) {
            new ClassReader(in).accept(node,ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        return node;
    }
    @Test void pinnedRemoveBlockSignatureAndProductionMapping() throws Exception {
        String descriptor="(Lnet/minecraft/core/BlockPos;Z)Z";
        var level=read("net/minecraft/world/level/Level");
        assertEquals(1,level.methods.stream().filter(m->m.name.equals("removeBlock")&&m.desc.equals(descriptor)).count(),
                "Level.removeBlock(BlockPos,boolean) must exist");
        var lines=Files.readAllLines(Path.of("build/createMcpToSrg/output.tsrg"));
        int start=lines.indexOf("net/minecraft/world/level/Level net/minecraft/world/level/Level");
        assertTrue(start>=0);
        boolean found=false;
        for(int i=start+1;i<lines.size()&&lines.get(i).startsWith("\t");i++)
            if(lines.get(i).equals("\tremoveBlock "+descriptor+" m_7471_"))found=true;
        assertTrue(found,"Production removeBlock mapping changed; audit the mixin");
        var mixin=read("dev/expedition/bridge/mixin/ProtectedBlockRemovalMixin");
        var hook=mixin.methods.stream().filter(m->m.name.equals("expedition$protectSilentRemoval")).findFirst().orElseThrow();
        var inject=hook.visibleAnnotations.stream().filter(a->a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        var values=new HashMap<String,Object>();
        for(int i=0;i<inject.values.size();i+=2)values.put((String)inject.values.get(i),inject.values.get(i+1));
        assertEquals(List.of("removeBlock","m_7471_"),values.get("method"));
        assertEquals(1,values.get("require"));
        assertEquals(1,values.get("allow"));
        assertEquals(Boolean.FALSE,values.get("remap"));
        var config=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/expedition_bridge.mixins.json"))).getAsJsonObject().getAsJsonArray("mixins");
        assertEquals(1,config.asList().stream().filter(e->e.getAsString().equals("ProtectedBlockRemovalMixin")).count());
    }
}
