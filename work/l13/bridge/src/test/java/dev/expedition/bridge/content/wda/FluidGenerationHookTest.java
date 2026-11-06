package dev.expedition.bridge.content.wda;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Inspect real mapped classes and production SRG names without bootstrapping a Minecraft world. */
class FluidGenerationHookTest {
    private ClassNode read(String name) throws Exception {
        var node=new ClassNode();try(var in=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(name+".class"))) {
            new ClassReader(in).accept(node,ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }return node;
    }
    @Test void pinnedTickNamesAndBothVanillaFluidsUseTheRegisteredHook() throws Exception {
        String descriptor="(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/FluidState;)V";
        var node=read("net/minecraft/world/level/material/FlowingFluid");
        assertEquals(1,node.methods.stream().filter(m->m.name.equals("tick")&&m.desc.equals(descriptor)).count());
        for(String type:List.of("WaterFluid","WaterFluid$Flowing","WaterFluid$Source","LavaFluid","LavaFluid$Flowing","LavaFluid$Source"))
            assertTrue(read("net/minecraft/world/level/material/"+type).methods.stream().noneMatch(m->m.name.equals("tick")&&m.desc.equals(descriptor)));
        var lines=Files.readAllLines(Path.of("build/createMcpToSrg/output.tsrg"));
        int start=lines.indexOf("net/minecraft/world/level/material/FlowingFluid net/minecraft/world/level/material/FlowingFluid");
        assertTrue(start>=0);boolean found=false;
        for(int i=start+1;i<lines.size()&&lines.get(i).startsWith("\t");i++)
            if(lines.get(i).equals("\ttick "+descriptor+" m_6292_"))found=true;
        assertTrue(found,"Production tick mapping changed; audit Mixin");
        var mixin=read("dev/expedition/bridge/mixin/FluidGenerationMixin");
        var hook=mixin.methods.stream().filter(m->m.name.equals("expedition$stageFluid")).findFirst().orElseThrow();
        var inject=hook.visibleAnnotations.stream().filter(a->a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        var values=new HashMap<String,Object>();for(int i=0;i<inject.values.size();i+=2)values.put((String)inject.values.get(i),inject.values.get(i+1));
        assertEquals(List.of("tick","m_6292_"),values.get("method"));
        assertEquals(1,values.get("require"));assertEquals(1,values.get("allow"));assertEquals(Boolean.FALSE,values.get("remap"));
        var config=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/expedition_bridge.mixins.json"))).getAsJsonObject().getAsJsonArray("mixins");
        assertEquals(1,config.asList().stream().filter(e->e.getAsString().equals("FluidGenerationMixin")).count());
        assertFalse(config.asList().stream().anyMatch(e->e.getAsString().equals("WaterGenerationMixin")));
    }
}
