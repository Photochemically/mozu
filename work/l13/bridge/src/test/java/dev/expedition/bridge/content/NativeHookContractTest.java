package dev.expedition.bridge.content;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

/** Verify the pinned upstream hook locations without loading DD/Forge classes into plain JUnit. */
class NativeHookContractTest {
    private List<MethodInsnNode> calls(ZipFile jar,String owner,String method) throws Exception {
        var node=new ClassNode();
        try(var input=jar.getInputStream(jar.getEntry(owner+".class"))) {new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);}
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
}
