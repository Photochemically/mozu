package dev.expedition.bridge.mixin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the Create 6.0.8 API surface used by the disassembly guard and the movement-allowed
 * registration. The guard reaches Create through reflection, so a changed runtime must be
 * re-reviewed here instead of failing silently in the field.
 */
class CreateApiContractTest {
    private static ClassNode read(String jarPath, String owner) throws Exception {
        try (var jar = new ZipFile(jarPath)) {
            var entry = jar.getEntry(owner + ".class");
            assertNotNull(entry, owner);
            var node = new ClassNode();
            try (var in = jar.getInputStream(entry)) {
                new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
            return node;
        }
    }

    @Test void pinnedCreateApiForMovementChecksAndDisassemblyGuard() throws Exception {
        var jar = Path.of("libs/create-6.0.8.jar").toString();
        var checks = read(jar, "com/simibubi/create/api/contraption/BlockMovementChecks");
        assertTrue(checks.methods.stream().anyMatch(m -> m.name.equals("registerMovementAllowedCheck")
                && m.desc.equals("(Lcom/simibubi/create/api/contraption/BlockMovementChecks$MovementAllowedCheck;)V")
                && (m.access & Opcodes.ACC_STATIC) != 0), "movement-allowed registration");
        assertTrue(checks.methods.stream().anyMatch(m -> m.name.equals("isMovementAllowed")
                && m.desc.equals("(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z")
                && (m.access & Opcodes.ACC_STATIC) != 0), "movement-allowed query");
        var result = read(jar, "com/simibubi/create/api/contraption/BlockMovementChecks$CheckResult");
        assertEquals(Set.of("SUCCESS", "FAIL", "PASS"), result.fields.stream().filter(f -> (f.access & Opcodes.ACC_ENUM) != 0)
                .map(f -> f.name).collect(java.util.stream.Collectors.toSet()), "check results");
        var allowed = read(jar, "com/simibubi/create/api/contraption/BlockMovementChecks$MovementAllowedCheck");
        assertTrue(allowed.methods.stream().anyMatch(m -> m.name.equals("isMovementAllowed")
                && m.desc.equals("(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lcom/simibubi/create/api/contraption/BlockMovementChecks$CheckResult;")),
                "movement-allowed check signature");

        var entity = read(jar, "com/simibubi/create/content/contraptions/AbstractContraptionEntity");
        assertTrue(entity.methods.stream().anyMatch(m -> m.name.equals("disassemble") && m.desc.equals("()V")), "disassemble");
        assertTrue(entity.methods.stream().anyMatch(m -> m.name.equals("getContraption")
                && m.desc.equals("()Lcom/simibubi/create/content/contraptions/Contraption;")), "getContraption");
        assertTrue(entity.methods.stream().anyMatch(m -> m.name.equals("makeStructureTransform")
                && m.desc.equals("()Lcom/simibubi/create/content/contraptions/StructureTransform;")), "makeStructureTransform");

        var contraption = read(jar, "com/simibubi/create/content/contraptions/Contraption");
        assertTrue(contraption.fields.stream().anyMatch(f -> f.name.equals("blocks")
                && f.desc.equals("Ljava/util/Map;")), "contraption blocks field");
        var transform = read(jar, "com/simibubi/create/content/contraptions/StructureTransform");
        assertTrue(transform.methods.stream().anyMatch(m -> m.name.equals("apply")
                && m.desc.equals("(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;")), "structure transform apply");
    }
}
