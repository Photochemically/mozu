package dev.expedition.bridge.portal;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProtectionEventContractTest {
    private ClassNode read(String owner) throws Exception {
        var n=new ClassNode();try(var in=getClass().getClassLoader().getResourceAsStream(owner+".class")) {
            assertNotNull(in,owner);new ClassReader(in).accept(n,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }return n;
    }
    @Test void pinnedForgeMutationEventsAreCancelableAndSubscribed() throws Exception {
        var listener=read("dev/expedition/bridge/portal/ManagedDoorProtection");
        for(var type:List.of("level/BlockEvent$EntityPlaceEvent","level/PistonEvent$Pre","level/BlockEvent$BlockToolModificationEvent",
                "level/BlockEvent$FluidPlaceBlockEvent","entity/living/LivingDestroyBlockEvent")) {
            String name="net/minecraftforge/event/"+type;var event=read(name);
            assertTrue(event.visibleAnnotations.stream().anyMatch(a->a.desc.equals("Lnet/minecraftforge/eventbus/api/Cancelable;")),name);
            var methods=listener.methods.stream().filter(m->m.desc.equals("(L"+name+";)V")).toList();assertEquals(1,methods.size(),name);
            assertTrue(methods.get(0).visibleAnnotations.stream().anyMatch(a->a.desc.equals("Lnet/minecraftforge/eventbus/api/SubscribeEvent;")));
        }
        var multi=read("net/minecraftforge/event/level/BlockEvent$EntityMultiPlaceEvent");
        assertEquals("net/minecraftforge/event/level/BlockEvent$EntityPlaceEvent",multi.superName);
        assertTrue(multi.methods.stream().anyMatch(m->m.name.equals("getReplacedBlockSnapshots")&&m.desc.equals("()Ljava/util/List;")));
    }
}
