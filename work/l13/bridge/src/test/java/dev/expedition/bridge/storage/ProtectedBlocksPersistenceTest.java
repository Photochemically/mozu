package dev.expedition.bridge.storage;

import dev.expedition.bridge.domain.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProtectedBlocksPersistenceTest {
    private final String dimension="dimdoors:dungeon_pockets";
    private PortalRecord.Site site(int x,int y,int z){return new PortalRecord.Site(dimension,x,y,z);}
    private PortalRecord portal() {
        var p=new PortalRecord(UUID.randomUUID(),PortalKind.EXPLORATION,3,new PortalRecord.Site("minecraft:overworld",2,70,2),19);
        p.reserve("dimdoors:test",new PortalRecord.Destination(dimension,4,new BlockBounds(0,0,0,40,40,40),site(2,2,2)));
        p.generationStarted();return p;
    }
    @Test void exactPositionsPersistWithRoutesRewardsAndNoBlanketProtection() {
        var p=portal();var original=List.of(site(2,1,2),site(2,2,2),site(2,3,2),site(15,15,15));
        p.installProtectedBlocks(original);p.registerExit(site(2,2,2));p.registerInternalLink(site(5,5,5),site(9,5,9));
        p.recordBossDefeat("test:boss");p.generationComplete();var data=new ExpeditionSavedData();data.add(p);
        var saved=data.save(new CompoundTag());assertEquals(6,saved.getInt("Schema"));
        var restored=ExpeditionSavedData.load(saved).get(p.id());assertEquals(Set.copyOf(original),restored.protectedBlocks());
        assertEquals(p.internalLinks(),restored.internalLinks());assertEquals(p.exits(),restored.exits());assertEquals(p.defeatedBosses(),restored.defeatedBosses());
        assertEquals(p.phase(),restored.phase());assertEquals(p.destination(),restored.destination());
        assertFalse(restored.isProtectedBlock(site(14,15,15)));assertFalse(restored.isProtectedBlock(new PortalRecord.Site("minecraft:overworld",15,15,15)));
        assertThrows(UnsupportedOperationException.class,()->restored.protectedBlocks().clear());
        assertThrows(IllegalStateException.class,()->restored.installProtectedBlocks(List.of(site(20,20,20))));
    }
    @Test void interruptedGenerationRetainsProtectionWhileQuarantiningTheInstance() {
        var p=portal();p.installProtectedBlocks(Set.of(site(5,5,5)));var data=new ExpeditionSavedData();data.add(p);
        var restored=ExpeditionSavedData.load(data.save(new CompoundTag())).get(p.id());
        assertEquals(PortalRecord.Phase.FAILED,restored.phase());assertTrue(restored.isProtectedBlock(site(5,5,5)));
        assertThrows(IllegalStateException.class,()->restored.generationStarted());
    }
    @Test void oldSchemasHaveNoInventedDeviceListAndCurrentSchemaCannotSilentlyLoseIt() {
        for(int schema=1;schema<=5;schema++) {
            var p=portal();p.generationComplete();var data=new ExpeditionSavedData();data.add(p);
            var saved=data.save(new CompoundTag());saved.putInt("Schema",schema);saved.getList("Portals",10).getCompound(0).remove("ProtectedBlocks");
            var restored=ExpeditionSavedData.load(saved);assertTrue(restored.get(p.id()).protectedBlocks().isEmpty());
            assertEquals(6,restored.save(new CompoundTag()).getInt("Schema"));
        }
        var data=new ExpeditionSavedData();data.add(portal());var saved=data.save(new CompoundTag());
        saved.getList("Portals",10).getCompound(0).remove("ProtectedBlocks");
        assertThrows(IllegalArgumentException.class,()->ExpeditionSavedData.load(saved));
    }
    @Test void invalidBatchesFailBeforeAnyPositionIsAdded() {
        var p=portal();var valid=site(2,1,2);
        for(var batch:List.of(List.of(valid,valid),List.of(valid,site(41,2,2)),List.of(valid,new PortalRecord.Site("minecraft:overworld",2,1,2)))) {
            assertThrows(IllegalArgumentException.class,()->p.installProtectedBlocks(batch));assertTrue(p.protectedBlocks().isEmpty());
        }
        assertThrows(IllegalArgumentException.class,()->p.installProtectedBlocks(Collections.nCopies(PortalRecord.MAX_PROTECTED_BLOCKS+1,valid)));
        p.installProtectedBlocks(List.of(valid));assertThrows(IllegalArgumentException.class,()->p.installProtectedBlocks(List.of(site(3,3,3))));
        assertEquals(Set.of(valid),p.protectedBlocks());
    }
    @Test void malformedSavedCoordinatesDuplicatesAndWrongTypesAreRejected() {
        var p=portal();p.installProtectedBlocks(List.of(site(3,3,3)));var good=PortalNbt.write(p);
        var duplicate=good.copy();duplicate.getList("ProtectedBlocks",10).add(duplicate.getList("ProtectedBlocks",10).getCompound(0).copy());
        assertThrows(IllegalArgumentException.class,()->PortalNbt.read(duplicate));
        for(var field:List.of("X","Y","Z","Dimension")) {
            var missing=good.copy();missing.getList("ProtectedBlocks",10).getCompound(0).remove(field);
            assertThrows(IllegalArgumentException.class,()->PortalNbt.read(missing));
        }
        var bad=good.copy();bad.putString("ProtectedBlocks","corrupt");assertThrows(IllegalArgumentException.class,()->PortalNbt.read(bad));
        var wrongList=good.copy();var values=new ListTag();values.add(IntTag.valueOf(1));wrongList.put("ProtectedBlocks",values);
        assertThrows(IllegalArgumentException.class,()->PortalNbt.read(wrongList));
        var outside=good.copy();outside.getList("ProtectedBlocks",10).getCompound(0).putInt("Y",100);
        assertThrows(IllegalArgumentException.class,()->PortalNbt.read(outside));
    }
}
