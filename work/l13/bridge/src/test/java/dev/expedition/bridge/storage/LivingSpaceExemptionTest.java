package dev.expedition.bridge.storage;

import dev.expedition.bridge.domain.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Living-space exemption at the data layer: player-built iron/quartz doors create ordinary
 * DD pockets that carry no PortalRecord and no PocketIdentity, so no protection query can
 * ever hit them. The journaled data must only answer for the exact recorded positions.
 */
class LivingSpaceExemptionTest {
    private static PortalRecord.Site site(String dimension, int x, int y, int z) {
        return new PortalRecord.Site(dimension, x, y, z);
    }

    private ExpeditionSavedData data;
    private PortalRecord exploration;

    private void journaledExploration() {
        data = new ExpeditionSavedData();
        exploration = new PortalRecord(UUID.randomUUID(), PortalKind.EXPLORATION, 3,
                site("minecraft:overworld", 10, 70, 10), 1L);
        exploration.preparationStarted("dungeon/chain_t");
        exploration.reserve("dungeon/chain_t", new PortalRecord.Destination("dimdoors:dungeon_pockets", 42,
                new BlockBounds(100, 30, 100, 131, 59, 131), site("dimdoors:dungeon_pockets", 115, 45, 115)));
        data.add(exploration);
    }

    @Test void livingSpacePositionsAnswerNoProtectionQueries() {
        journaledExploration();
        // A player's own pocket sits at unrelated dungeon coordinates without any journal entry.
        var livingPos = site("dimdoors:dungeon_pockets", 900, 40, 900);
        assertNull(data.at(livingPos), "living-space position must not resolve to a portal");
        for (int dy = -1; dy <= 1; dy++) {
            assertNull(data.at(site("dimdoors:dungeon_pockets", 900, 40 + dy, 900)));
        }
        assertFalse(exploration.isProtectedBlock(livingPos));
        assertFalse(exploration.exits().contains(livingPos));
        assertFalse(exploration.isInternalDevice(livingPos));
    }

    @Test void protectionAnswersExactlyTheRecordedPositionsOnly() {
        journaledExploration();
        exploration.generationStarted();
        exploration.installProtectedBlocks(Set.of(
                site("dimdoors:dungeon_pockets", 115, 45, 115),
                site("dimdoors:dungeon_pockets", 116, 45, 115)));
        exploration.registerExit(site("dimdoors:dungeon_pockets", 120, 45, 120));
        exploration.registerInternalLink(site("dimdoors:dungeon_pockets", 110, 45, 110),
                site("dimdoors:dungeon_pockets", 125, 45, 125));
        assertTrue(exploration.isProtectedBlock(site("dimdoors:dungeon_pockets", 115, 45, 115)));
        assertFalse(exploration.isProtectedBlock(site("dimdoors:dungeon_pockets", 115, 46, 115)),
                "neighbouring ordinary cells stay unprotected");
        assertTrue(exploration.exits().contains(site("dimdoors:dungeon_pockets", 120, 45, 120)));
        assertFalse(exploration.exits().contains(site("dimdoors:dungeon_pockets", 121, 45, 120)));
        assertTrue(exploration.isInternalDevice(site("dimdoors:dungeon_pockets", 110, 45, 110)));
        assertFalse(exploration.isInternalDevice(site("dimdoors:dungeon_pockets", 110, 46, 110)));
    }

    @Test void unrelatedOverworldPositionsStayUntouched() {
        journaledExploration();
        for (var pos : List.of(
                site("minecraft:overworld", 10, 70, 11),
                site("minecraft:overworld", 11, 70, 10),
                site("minecraft:overworld", 9, 70, 10),
                site("minecraft:the_nether", 10, 70, 10))) {
            assertNull(data.at(pos), "only the exact entrance cell answers: " + pos);
        }
        assertNotNull(data.at(site("minecraft:overworld", 10, 70, 10)));
    }
}
