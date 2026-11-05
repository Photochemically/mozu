package dev.expedition.bridge.diagnostics;

import com.google.gson.*;
import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.storage.ExpeditionSavedData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class AcceptanceFixturesTest {
    private final PortalRecord.Site site=new PortalRecord.Site("minecraft:overworld",4,70,4);
    @Test void sevenTypesPinOnlyTestSelectionsWithoutAllocationsOrNaturalStateAndSurviveReload() {
        assertEquals(Set.of(NaturalEntryPlan.Type.values()),AcceptanceFixtures.all().stream().map(AcceptanceFixtures.Fixture::door).collect(Collectors.toSet()));
        var data=new ExpeditionSavedData();int x=4;
        for(var f:AcceptanceFixtures.all()) {
            var record=f.record(new PortalRecord.Site("minecraft:overworld",x,70,4),AcceptanceFixtures.DEFAULT_SEED);x+=16;
            assertNull(record.destination());assertEquals(PortalRecord.Phase.NEW,record.phase());assertTrue(record.visits().snapshot().isEmpty());
            if(record.kind()==PortalKind.GOLD)assertNull(record.selection());
            else {assertEquals(f.choice(),record.selection());assertTrue(record.selection().content().id().startsWith("expedition_bridge:acceptance/"));}
            data.add(record);
        }
        var restored=ExpeditionSavedData.load(data.save(new CompoundTag()));
        assertEquals(7,restored.records().size());assertNull(restored.naturalPolicy());assertTrue(restored.travel().operations().isEmpty());
        for(var p:data.records()) {
            var copy=restored.get(p.id());assertEquals(p.selection(),copy.selection());assertEquals(p.entrance(),copy.entrance());
            assertEquals(PortalRecord.Phase.NEW,copy.phase());assertNull(copy.destination());assertFalse(restored.naturalAttempted(p.id()));
        }
        assertFalse(DungeonCatalog.disabled().enabled());assertTrue(DungeonCatalog.disabled().entries().isEmpty());
    }
    @Test void repeatedProvisioningHasStableIdentityAndCannotReplaceSavedRecords() {
        for(var f:AcceptanceFixtures.all()) {
            var p=f.record(site,42);var data=new ExpeditionSavedData();data.add(p);
            p.quarantine("simulated interrupted placement");
            var restored=ExpeditionSavedData.load(data.save(new CompoundTag()));var retry=f.record(site,42);
            assertEquals(p.id(),retry.id());assertThrows(IllegalArgumentException.class,()->restored.add(retry));
            assertEquals(PortalRecord.Phase.FAILED,restored.get(retry.id()).phase());
            assertNotEquals(p.id(),f.record(site,43).id());
            assertNotEquals(p.id(),f.record(new PortalRecord.Site("minecraft:overworld",12,70,4),42).id());
            assertThrows(IllegalArgumentException.class,()->f.record(new PortalRecord.Site("dimdoors:dungeon_pockets",4,70,4),42));
        }
    }
    @Test void representativeCasesHaveOriginalResourceEvidenceAndKeepRewardRoomSeparate() throws Exception {
        var audit=JsonParser.parseString(Files.readString(Path.of("../audit/content-catalog-audit.json"))).getAsJsonObject();
        for(var f:AcceptanceFixtures.all()) {
            if(f.choice()==null)continue;
            var spec=f.choice().content();assertNotEquals("dimdoors:dungeon/upwards_t_treasure",spec.target());
            String group=spec.provider()==DungeonSpec.Provider.WDA?"structures":"native";
            var matches=new ArrayList<JsonObject>();for(var entry:audit.getAsJsonArray(group)) {
                var item=entry.getAsJsonObject();if(spec.target().equals(item.get("id").getAsString()))matches.add(item);
            }
            assertEquals(1,matches.size(),spec.target());var item=matches.get(0);
            if(spec.provider()==DungeonSpec.Provider.WDA) {
                assertTrue(item.getAsJsonObject("graph").getAsJsonArray("missing").isEmpty());
                assertTrue(item.getAsJsonObject("graph").getAsJsonArray("unsupported").isEmpty());
                if(spec.target().endsWith(":keep_kayra")) {
                    assertEquals(250,item.getAsJsonArray("maxSingleTemplateDimensions").get(1).getAsInt());
                    assertEquals(512,f.minimumPocketHeight());assertEquals(4,spec.exits());
                }
            } else {
                assertTrue(item.get("directSchematicGenerator").getAsBoolean());assertEquals("default",item.get("resourcePack").getAsString());
                assertFalse(item.getAsJsonArray("originalRifts").isEmpty());assertEquals(0,spec.exits());
            }
        }
        var unstable=AcceptanceFixtures.all().stream().filter(f->f.door()==NaturalEntryPlan.Type.UNSTABLE).findFirst().orElseThrow();
        assertEquals(3,unstable.choice().tier());assertTrue(unstable.door().block.endsWith("minecraft_bamboo_door"));
    }
    @Test void keepTestTerrainAlignsWithOriginalLowFloorWithoutCroppingTheTallTemplate() throws Exception {
        var fixture=AcceptanceFixtures.all().stream().filter(f->f.id().equals("crimson_keep")).findFirst().orElseThrow();
        var e=fixture.choice().content().environment();
        var evidence=JsonParser.parseString(Files.readString(Path.of("../audit/keep-kayra-ground-evidence.json"))).getAsJsonObject();
        int base=evidence.get("firstPieceMinY").getAsInt(),feet=evidence.get("localFeetY").getAsInt();
        assertEquals(base+feet,e.firstFreeY());assertEquals(65,e.firstFreeY());
        assertEquals(31,evidence.get("formerTestFirstFreeY").getAsInt()-e.firstFreeY());
        assertTrue(evidence.get("reachableConservativeInteriorCells").getAsInt()>50);
        assertEquals(165,evidence.getAsJsonArray("exampleLocalRoute").get(0).getAsJsonArray().get(0).getAsInt());
        var building=new BlockBounds(-81,base,-81,81,base+249,81);
        var island=new IslandPlan(building,e.firstFreeY(),e.padding(),e.rim(),e.foundationDepth(),e.surface());
        assertThrows(IllegalArgumentException.class,()->ReservationGeometry.plan(island.completeBounds(),16,0,256));
        var reservation=ReservationGeometry.plan(island.completeBounds(),16,0,512);
        assertEquals(314,reservation.padded().height());assertEquals(250,building.height());
        var moved=reservation.translatedContent(4096,8192);
        var shifted=island.move(moved.minX()-island.completeBounds().minX(),moved.minY()-island.completeBounds().minY(),moved.minZ()-island.completeBounds().minZ());
        assertEquals(feet,shifted.firstFreeY()-shifted.building().minY());
        assertTrue(shifted.completeBounds().contains(shifted.entryX(),shifted.firstFreeY()+1,shifted.entryZ()));
    }
}
