package dev.expedition.bridge.content;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

/** Verify the pinned upstream hook locations without loading DD/Forge classes into plain JUnit. */
class NativeHookContractTest {
    private ClassNode read(ZipFile jar,String owner) throws Exception {
        var node=new ClassNode();
        try(var input=jar.getInputStream(jar.getEntry(owner+".class"))) {new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);}
        return node;
    }
    private List<MethodInsnNode> calls(ZipFile jar,String owner,String method) throws Exception {
        var node=read(jar,owner);
        var methods=node.methods.stream().filter(m->m.name.equals(method)).toList();assertEquals(1,methods.size());
        var result=new ArrayList<MethodInsnNode>();
        for(var instruction:methods.get(0).instructions)if(instruction instanceof MethodInsnNode call)result.add(call);
        return result;
    }
    private int single(List<MethodInsnNode> calls,String owner,String name,String descriptor) {
        var matches=calls.stream().filter(i->i.owner.equals(owner)&&i.name.equals(name)&&i.desc.equals(descriptor)).toList();
        assertEquals(1,matches.size(),owner+"."+name);return calls.indexOf(matches.get(0));
    }
    @Test void allocationAndFinalShellHooksPrecedeNativePlacement() throws Exception {
        String base="org/dimdev/dimdoors/";
        try(var jar=new ZipFile("libs/dimdoors-5.4.4.jar")) {
            var schematic=calls(jar,base+"pockets/generator/SchematicGenerator","prepareAndPlacePocket");
            int allocate=single(schematic,base+"world/pocket/PocketDirectory","newPocket","(L"+base+"world/pocket/type/Pocket$PocketBuilder;)L"+base+"world/pocket/type/Pocket;");
            int rifts=single(schematic,base+"pockets/PocketTemplate","getAbsoluteRifts","(L"+base+"world/pocket/type/Pocket;)Ljava/util/Map;");
            assertTrue(allocate<rifts);
            var reference=calls(jar,base+"pockets/virtual/reference/PocketGeneratorReference","prepareAndPlacePocket");
            int resolved=single(reference,base+"pockets/virtual/reference/PocketGeneratorReference","getReferencedPocketGenerator","(L"+base+"pockets/PocketGenerationContext;)L"+base+"pockets/generator/PocketGenerator;");
            int attach=single(reference,base+"pockets/generator/LazyPocketGenerator","attachToPocket","(L"+base+"world/pocket/type/LazyGenerationPocket;)V");
            int init=single(reference,base+"world/pocket/type/LazyGenerationPocket","init","()V");
            assertTrue(resolved<attach && attach<init);
        }
    }
    @Test void sameNativeSchematicGeneratorSuppliesChunkPlacementLootAndDeferredRifts() throws Exception {
        String base="org/dimdev/dimdoors/";
        try(var jar=new ZipFile("libs/dimdoors-5.4.4.jar")) {
            var prepare=read(jar,base+"pockets/generator/SchematicGenerator").methods.stream()
                    .filter(m->m.name.equals("prepareAndPlacePocket")).findFirst().orElseThrow();
            assertTrue(Arrays.stream(prepare.instructions.toArray()).anyMatch(i->i instanceof TypeInsnNode t
                    &&t.getOpcode()==Opcodes.INSTANCEOF&&t.desc.equals(base+"world/pocket/type/LazyGenerationPocket")));
            var branches=calls(jar,base+"pockets/generator/SchematicGenerator","prepareAndPlacePocket");
            assertTrue(branches.stream().anyMatch(c->c.owner.equals(base+"pockets/PocketTemplate")&&c.name.equals("place")));
            assertTrue(branches.stream().anyMatch(c->c.owner.equals(base+"pockets/modifier/AbsoluteRiftBlockEntityModifier")&&c.name.equals("<init>")));
            var chunk=calls(jar,base+"pockets/generator/SchematicGenerator","generateChunk");
            int place=single(chunk,base+"pockets/PocketTemplate","place","(L"+base+"world/pocket/type/LazyGenerationPocket;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;L"+base+"api/util/BlockPlacementType;)V");
            int loot=single(chunk,base+"pockets/generator/SchematicGenerator","setupChunk","(L"+base+"world/pocket/type/Pocket;Lnet/minecraft/world/level/chunk/ChunkAccess;Z)V");
            assertTrue(place<loot);
            assertTrue(chunk.stream().anyMatch(c->c.name.equals("isSetupLoot")));
            assertTrue(chunk.stream().anyMatch(c->c.owner.equals(base+"pockets/generator/LazyPocketGenerator")&&c.name.equals("generateChunk")));
        }
    }
    @Test void labProtectionHasActiveEventEnforcementAndCannotBeTreatedAsAnUnusedAddon() throws Exception {
        String base="org/dimdev/dimdoors/";int enforced=0;
        try(var jar=new ZipFile("libs/dimdoors-5.4.4.jar")) {
            for(var method:read(jar,base+"DimensionalDoors").methods) {
                var instructions=Arrays.asList(method.instructions.toArray());
                if(instructions.stream().noneMatch(i->i instanceof LdcInsnNode ldc&&ldc.cst instanceof Type type
                        &&type.getInternalName().equals(base+"world/pocket/type/addon/PreventBlockModificationAddon")))continue;
                assertTrue(instructions.stream().anyMatch(i->i instanceof MethodInsnNode c&&c.name.equals("applicableAddons")));
                assertTrue(instructions.stream().anyMatch(i->i instanceof MethodInsnNode c&&c.name.equals("interruptFalse")));
                enforced++;
            }
        }
        assertEquals(2,enforced,"Both block breaking and placement must remain protected until a scoped adapter exists");
    }
    @Test void minecraftRegistryUsesMobSpawnerForBlockEntitiesNotTheBlockIdentifier() throws Exception {
        var node=new ClassNode();
        try(var in=getClass().getClassLoader().getResourceAsStream("net/minecraft/world/level/block/entity/BlockEntityType.class")) {
            assertNotNull(in);new ClassReader(in).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        var init=node.methods.stream().filter(m->m.name.equals("<clinit>")).findFirst().orElseThrow();
        var ids=Arrays.stream(init.instructions.toArray()).filter(i->i instanceof LdcInsnNode l&&"mob_spawner".equals(l.cst)).toList();
        assertEquals(1,ids.size());boolean registered=false,assigned=false;
        for(var i=ids.get(0).getNext();i!=null;i=i.getNext()) {
            if(i instanceof MethodInsnNode c&&c.owner.equals(node.name)&&c.name.equals("register"))registered=true;
            if(i instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.PUTSTATIC) {
                assertTrue(registered);assertEquals(node.name,f.owner);assertEquals("MOB_SPAWNER",f.name);assigned=true;break;
            }
        }
        assertTrue(assigned,"Do not rename valid native minecraft:mob_spawner payloads to minecraft:spawner");
    }
}
