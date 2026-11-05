package dev.expedition.bridge.storage;

import com.google.gson.JsonParser;
import dev.expedition.bridge.content.DungeonSpecJson;
import dev.expedition.bridge.domain.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SelectionPersistenceTest {
    private PortalRecord portal(){return new PortalRecord(UUID.randomUUID(),PortalKind.UNSTABLE,0,new PortalRecord.Site("minecraft:overworld",0,70,0),42);}
    private DungeonCatalog.Choice choice(){return new DungeonCatalog.Choice(4,new DungeonSpec("test:building",DungeonSpec.Provider.WDA,
            "dungeons_arise:test",Set.of(3,4),7,new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.ROCK),3));}
    @Test void selectedSnapshotAndUnstableTierSurviveReloadBeforeAnyAllocation() {
        var portal=portal();portal.selectContent(choice());var data=new ExpeditionSavedData();data.add(portal);
        var loaded=ExpeditionSavedData.load(data.save(new CompoundTag())).get(portal.id());
        assertEquals(portal.selection(),loaded.selection());assertEquals(4,loaded.effectiveTier());
        assertEquals(PortalRecord.Phase.NEW,loaded.phase());assertNull(loaded.destination());
    }
    @Test void interruptedGenerationRetainsSelectionWithoutPickingAnotherBuilding() {
        var portal=portal();portal.selectContent(choice());portal.preparationStarted(portal.selection().content().target());
        var data=new ExpeditionSavedData();data.add(portal);var loaded=ExpeditionSavedData.load(data.save(new CompoundTag())).get(portal.id());
        assertEquals(PortalRecord.Phase.FAILED,loaded.phase());assertEquals(choice(),loaded.selection());
    }
    @Test void schemaTwoWithoutSelectionsIsReadWithoutCreatingOrGuessingContent() {
        var portal=portal();var data=new ExpeditionSavedData();data.add(portal);var saved=data.save(new CompoundTag());saved.putInt("Schema",2);
        var loaded=ExpeditionSavedData.load(saved);assertNull(loaded.get(portal.id()).selection());assertEquals(5,loaded.save(new CompoundTag()).getInt("Schema"));
    }
    @Test void configurationRoundTripIsExactAndFractionalIntegersAreRejected() {
        var spec=choice().content();var json=DungeonSpecJson.write(spec);assertEquals(spec,DungeonSpecJson.read(spec.id(),json));
        json.add("weight",JsonParser.parseString("1.5"));assertThrows(ArithmeticException.class,()->DungeonSpecJson.read(spec.id(),json));
    }
    @Test void waterDepthSurvivesSaveAndRestartAsPartOfThePinnedSelection() {
        var portal=portal();var spec=new DungeonSpec("test:ship",DungeonSpec.Provider.WDA,"dungeons_arise:illager_galley",
                Set.of(4),1,new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.WATER,12),2);
        portal.selectContent(new DungeonCatalog.Choice(4,spec));var data=new ExpeditionSavedData();data.add(portal);
        var loaded=ExpeditionSavedData.load(data.save(new CompoundTag())).get(portal.id());
        assertEquals(spec,loaded.selection().content());assertEquals(12,loaded.selection().content().environment().waterDepth());
        assertEquals(PortalRecord.Phase.NEW,loaded.phase());assertNull(loaded.destination());
    }
    @Test void legacyDrySnapshotIsUnchangedWhileWaterRequiresAnExplicitIntegerDepth() {
        var spec=choice().content();var json=DungeonSpecJson.write(spec);var env=json.getAsJsonObject("environment");
        assertFalse(env.has("water_depth"));assertEquals(0,DungeonSpecJson.read(spec.id(),json).environment().waterDepth());
        env.addProperty("surface","water");assertThrows(IllegalArgumentException.class,()->DungeonSpecJson.read(spec.id(),json));
        env.add("water_depth",JsonParser.parseString("2.5"));assertThrows(ArithmeticException.class,()->DungeonSpecJson.read(spec.id(),json));
    }
    @Test void boardingPolicyIsPinnedWithoutChangingOlderShoreOnlySnapshots() {
        var original=choice().content();var old=DungeonSpecJson.write(original);
        assertFalse(DungeonSpecJson.read(original.id(),old).environment().boarding());
        var ship=new DungeonSpec("test:ship",DungeonSpec.Provider.WDA,"dungeons_arise:illager_galley",Set.of(4),1,
                new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.WATER,12,true),2);
        var portal=portal();portal.selectContent(new DungeonCatalog.Choice(4,ship));
        var loaded=PortalNbt.read(PortalNbt.write(portal));assertEquals(ship,loaded.selection().content());
        assertTrue(loaded.selection().content().environment().boarding());
        var json=DungeonSpecJson.write(ship);json.getAsJsonObject("environment").addProperty("boarding","true");
        assertThrows(IllegalArgumentException.class,()->DungeonSpecJson.read(ship.id(),json));
        assertThrows(IllegalArgumentException.class,()->new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.ROCK,0,true));
    }
}
