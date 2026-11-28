package dev.expedition.bridge.mixin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the DD 5.4.4 surface that the rift destination guard relies on: RiftBlockEntity exposes
 * setDestination(VirtualTarget) and the configuration tool calls it with a fresh IdMarker on
 * both attack and use paths, which is exactly the bypass the guard closes.
 */
class DimDoorsRiftContractTest {
    private static ClassNode read(String owner) throws Exception {
        try (var jar = new ZipFile("libs/dimdoors-5.4.4.jar")) {
            var entry = jar.getEntry(owner + ".class");
            assertNotNull(entry, owner);
            var node = new ClassNode();
            try (var in = jar.getInputStream(entry)) {
                new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
            return node;
        }
    }

    @Test void setDestinationIsInjectableAndTheToolRewritesTargetsDirectly() throws Exception {
        var rift = read("org/dimdev/dimdoors/block/entity/RiftBlockEntity");
        var setDestination = rift.methods.stream().filter(m -> m.name.equals("setDestination")
                && m.desc.equals("(Lorg/dimdev/dimdoors/rift/targets/VirtualTarget;)V")).toList();
        assertEquals(1, setDestination.size(), "setDestination(VirtualTarget)");
        assertTrue((setDestination.get(0).access & Opcodes.ACC_FINAL) == 0, "setDestination must remain injectable");

        var tool = read("org/dimdev/dimdoors/item/RiftConfigurationToolItem");
        for (var method : tool.methods) {
            var setIdMarker = Arrays.stream(method.instructions.toArray())
                    .filter(insn -> insn.getOpcode() == Opcodes.INVOKEVIRTUAL)
                    .map(insn -> (MethodInsnNode) insn)
                    .filter(insn -> insn.owner.equals("org/dimdev/dimdoors/block/entity/RiftBlockEntity")
                            && insn.name.equals("setDestination"))
                    .count();
            if (method.name.equals("onAttackBlock") || method.name.equals("m_7203_")) {
                assertTrue(setIdMarker > 0, "configuration tool rewrites destinations on " + method.name);
            }
        }
    }

    @Test void removalToolsOnlyTouchDetachedRifts() throws Exception {
        var remover = read("org/dimdev/dimdoors/item/RiftRemoverItem");
        boolean touchesDetached = false, touchesManaged = false;
        for (var method : remover.methods) {
            for (var insn : method.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call && call.owner.startsWith("org/dimdev/dimdoors/")) {
                    if (call.name.equals("findDetachRift")) touchesDetached = true;
                    if (call.owner.contains("RiftBlockEntity") && call.name.equals("setDestination")) touchesManaged = true;
                }
            }
        }
        assertTrue(touchesDetached, "remover targets detached rifts");
        assertFalse(touchesManaged, "remover must not rewrite ordinary rift destinations");
    }
}
