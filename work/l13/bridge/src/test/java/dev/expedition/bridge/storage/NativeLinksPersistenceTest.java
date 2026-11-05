package dev.expedition.bridge.storage;

import dev.expedition.bridge.domain.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeLinksPersistenceTest {
    private final PortalRecord.Site start=new PortalRecord.Site("dimdoors:dungeon_pockets",1,1,1),a=new PortalRecord.Site("dimdoors:dungeon_pockets",3,2,3),b=new PortalRecord.Site("dimdoors:dungeon_pockets",7,2,7);
    private PortalRecord room() {
        var p=new PortalRecord(UUID.randomUUID(),PortalKind.EXPLORATION,3,new PortalRecord.Site("minecraft:overworld",0,70,0),42);
        p.reserve("dimdoors:lab/telecommunications",new PortalRecord.Destination(start.dimension(),4,new BlockBounds(0,0,0,20,20,20),start));
        p.registerExit(start);p.generationStarted();p.generationComplete();return p;
    }
    @Test void internalRoutesAndCompletionSurviveReloadWithoutRebuilding() {
        var p=room();p.registerInternalLink(a,b);p.registerInternalLink(b,a);p.recordBossDefeat("test:finished");
        var data=new ExpeditionSavedData();data.add(p);var saved=data.save(new CompoundTag());assertEquals(5,saved.getInt("Schema"));
        var restored=ExpeditionSavedData.load(saved).get(p.id());
        assertEquals(p.internalLinks(),restored.internalLinks());assertEquals(Set.of(start),restored.exits());
        assertEquals(PortalRecord.Phase.READY,restored.phase());assertEquals(p.destination(),restored.destination());
        assertTrue(restored.defeatedBosses().contains("test:finished"));
        assertThrows(IllegalStateException.class,()->restored.preparationStarted("another:room"));
    }
    @Test void olderSchemasNeverInventInternalRoutesOrRewriteExistingExits() {
        for(int version=1;version<=4;version++) {
            var p=room();var data=new ExpeditionSavedData();data.add(p);var tag=data.save(new CompoundTag());tag.putInt("Schema",version);
            tag.getList("Portals",10).getCompound(0).remove("InternalLinks");
            var restored=ExpeditionSavedData.load(tag).get(p.id());
            assertTrue(restored.internalLinks().isEmpty());assertEquals(p.exits(),restored.exits());assertEquals(p.destination(),restored.destination());
        }
    }
    @Test void corruptedRoutesAreRejectedInsteadOfSilentlyDiscarded() {
        var p=room();p.registerInternalLink(a,b);var tag=PortalNbt.write(p);
        var list=tag.getList("InternalLinks",10);list.add(list.getCompound(0).copy());
        assertThrows(IllegalArgumentException.class,()->PortalNbt.read(tag));
        tag.putString("InternalLinks","damaged");assertThrows(IllegalArgumentException.class,()->PortalNbt.read(tag));
        var invalidList=new ListTag();invalidList.add(StringTag.valueOf("bad"));tag.put("InternalLinks",invalidList);
        assertThrows(IllegalArgumentException.class,()->PortalNbt.read(tag));
    }
}
