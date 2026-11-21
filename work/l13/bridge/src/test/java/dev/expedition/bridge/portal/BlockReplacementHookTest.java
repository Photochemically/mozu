package dev.expedition.bridge.portal;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Forge-patched bytecode and production mappings; not a live Mixin or world test. */
class BlockReplacementHookTest {
    private static final String MC="net/minecraft/",BRIDGE="dev/expedition/bridge/";
    private ClassNode read(String name) throws Exception {
        var node=new ClassNode();try(var in=getClass().getClassLoader().getResourceAsStream(name+".class")) {
            assertNotNull(in,name);new ClassReader(in).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }return node;
    }
    private List<MethodInsnNode> calls(String owner,String name) throws Exception {
        var methods=read(owner).methods.stream().filter(m->m.name.equals(name)).toList();assertEquals(1,methods.size(),owner+"."+name);
        return Arrays.stream(methods.get(0).instructions.toArray()).filter(i->i instanceof MethodInsnNode).map(i->(MethodInsnNode)i).toList();
    }
    private int first(List<MethodInsnNode> calls,String name) {
        for(int i=0;i<calls.size();i++)if(calls.get(i).name.equals(name))return i;
        fail("Missing native call "+name);return -1;
    }
    private void check(String owner,String method,String descriptor,String srg,String mixin,String callback,boolean withDescriptor) throws Exception {
        assertEquals(1,read(owner).methods.stream().filter(m->m.name.equals(method)&&m.desc.equals(descriptor)).count());
        var lines=Files.readAllLines(Path.of("build/createMcpToSrg/output.tsrg"));
        int start=lines.indexOf(owner+" "+owner);assertTrue(start>=0);boolean found=false;
        for(int i=start+1;i<lines.size()&&lines.get(i).startsWith("\t");i++)
            if(lines.get(i).equals("\t"+method+" "+descriptor+" "+srg))found=true;
        assertTrue(found,"Production target changed: "+owner+"."+method);
        var hook=read(BRIDGE+"mixin/"+mixin).methods.stream().filter(m->m.name.equals(callback)).findFirst().orElseThrow();
        var inject=hook.visibleAnnotations.stream().filter(a->a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        var values=new HashMap<String,Object>();for(int i=0;i<inject.values.size();i+=2)values.put((String)inject.values.get(i),inject.values.get(i+1));
        String suffix=withDescriptor?descriptor:"";
        assertEquals(List.of(method+suffix,srg+suffix),values.get("method"));
        assertEquals(1,values.get("require"));assertEquals(1,values.get("allow"));
        assertEquals(true,values.get("cancellable"));assertEquals(false,values.get("remap"));
        var at=(AnnotationNode)((List<?>)values.get("at")).get(0);assertEquals(List.of("value","HEAD"),at.values);
        var config=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/expedition_bridge.mixins.json")))
                .getAsJsonObject().getAsJsonArray("mixins");
        assertEquals(1,config.asList().stream().filter(e->e.getAsString().equals(mixin)).count());
    }
    @Test void chunkDestructionAndFlowHooksMatchBothPinnedDevelopmentAndProductionTargets() throws Exception {
        check(MC+"world/level/chunk/LevelChunk","setBlockState","(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
                "m_6978_","ProtectedBlockReplacementMixin","expedition$protectReplacement",false);
        check(MC+"world/level/Level","destroyBlock","(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
                "m_7740_","ProtectedBlockDestroyMixin","expedition$protectDestruction",true);
        check(MC+"world/level/material/FlowingFluid","spreadTo","(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/material/FluidState;)V",
                "m_6364_","FluidGenerationMixin","expedition$keepDevices",false);
    }
    @Test void nativeMutationSideEffectsExplainWhyLateReplacementRejectionAloneIsInsufficient() throws Exception {
        var chunk=calls(MC+"world/level/chunk/LevelChunk","setBlockState");
        assertTrue(first(chunk,"setBlockState")<first(chunk,"onRemove"));
        var destroy=calls(MC+"world/level/Level","destroyBlock");
        assertTrue(first(destroy,"dropResources")<first(destroy,"setBlock"));
        var flow=calls(MC+"world/level/material/FlowingFluid","spreadTo");
        assertTrue(first(flow,"beforeDestroyingBlock")<first(flow,"setBlock"));
        for(var type:List.of("WaterFluid","WaterFluid$Source","WaterFluid$Flowing"))
            assertTrue(read(MC+"world/level/material/"+type).methods.stream().noneMatch(m->m.name.equals("spreadTo")));
        var lava=calls(MC+"world/level/material/LavaFluid","spreadTo");
        assertTrue(lava.stream().anyMatch(c->c.name.equals("fireFluidPlaceBlockEvent")),"Lava conversion uses the existing Forge event guard");
        assertTrue(lava.stream().anyMatch(c->c.owner.equals(MC+"world/level/material/FlowingFluid")&&c.name.equals("spreadTo")));
    }
    @Test void lowLevelLookupNeverReadsOrLoadsChunksAndEntranceWritersUseNarrowPermissions() throws Exception {
        var lookup=calls(BRIDGE+"portal/ManagedDoorProtection","recordedAt");
        assertFalse(lookup.stream().anyMatch(c->Set.of("getBlockEntity","getBlockState","getChunk","getChunkAt").contains(c.name)));
        for(var entry:Map.of("ManagedEntrancePlacement","place","GoldEntranceFactory","place","PortalVisitEvents","removeLoadedEntrance").entrySet()) {
            var writes=calls(BRIDGE+"portal/"+entry.getKey(),entry.getValue());
            assertFalse(writes.stream().anyMatch(c->c.name.equals("setBlock")));
            assertEquals(2,writes.stream().filter(c->c.owner.equals(BRIDGE+"portal/ManagedBlockWrites")&&c.name.equals("setEntranceBlock")).count());
        }
    }
    @Test void pinnedPistonRetractionCanFinishTwoMovingEntitiesAfterThePreEvent() throws Exception {
        var calls=calls(MC+"world/level/block/piston/PistonBaseBlock","triggerEvent");
        var finals=calls.stream().filter(c->c.name.equals("finalTick")).toList();assertEquals(2,finals.size());
        var pres=calls.stream().filter(c->c.name.equals("onPistonMovePre")).toList();assertEquals(2,pres.size());
        for(var finish:finals)assertTrue(calls.indexOf(pres.get(1))<calls.indexOf(finish));
        assertTrue(calls.stream().anyMatch(c->c.name.equals("isExtending")));
    }
}
