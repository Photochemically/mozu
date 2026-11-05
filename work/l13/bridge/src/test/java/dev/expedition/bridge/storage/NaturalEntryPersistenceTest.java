package dev.expedition.bridge.storage;

import dev.expedition.bridge.domain.NaturalEntryPlan;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NaturalEntryPersistenceTest {
    @Test void attemptsAndLayoutSurviveReloadWithoutMakingAnyPocket() {
        var data=new ExpeditionSavedData();var policy=NaturalEntryPlan.Policy.defaults();data.initializeNaturalPolicy(policy);
        var attempt=new NaturalEntryPlan(8,policy).region(-1,0).get(0);data.recordNaturalAttempt(attempt.id());data.naturalPending(-19,true);
        var loaded=ExpeditionSavedData.load(data.save(new CompoundTag()));
        assertEquals(policy,loaded.naturalPolicy());assertTrue(loaded.naturalAttempted(attempt.id()));assertTrue(loaded.records().isEmpty());
        assertTrue(loaded.naturalPending(-19));loaded.naturalPending(-19,false);
        assertFalse(ExpeditionSavedData.load(loaded.save(new CompoundTag())).naturalPending(-19));
    }
    @Test void changedConfigurationDoesNotMoveOldSaveLayoutOrRefillConsumedSlots() {
        var data=new ExpeditionSavedData();data.initializeNaturalPolicy(NaturalEntryPlan.Policy.defaults());var id=UUID.randomUUID();data.recordNaturalAttempt(id);
        var loaded=ExpeditionSavedData.load(data.save(new CompoundTag()));
        assertEquals(NaturalEntryPlan.Policy.defaults(),loaded.initializeNaturalPolicy(new NaturalEntryPlan.Policy(8,1,1,64,List.of(1,1,1,1,1,1,1))));
        loaded.recordNaturalAttempt(id);var again=ExpeditionSavedData.load(loaded.save(new CompoundTag()));assertTrue(again.naturalAttempted(id));
    }
    @Test void previousSchemasDoNotInventNaturalEntries() {
        for(int version=1;version<=3;version++) {
            var tag=new CompoundTag();tag.putInt("Schema",version);var data=ExpeditionSavedData.load(tag);
            assertNull(data.naturalPolicy());assertTrue(data.records().isEmpty());assertEquals(5,data.save(new CompoundTag()).getInt("Schema"));
        }
    }
}
