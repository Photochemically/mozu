package dev.expedition.bridge.domain;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/** Static pack isolation and reservation checks. Runtime registry/codec loading requires a Forge acceptance server. */
class TallPocketCompatibilityTest {
    private static final Path PACK=Path.of("../test-packs/tall-exploration");
    private JsonObject json(Path path)throws Exception {return JsonParser.parseString(Files.readString(path)).getAsJsonObject();}
    @Test void onlyTheTwoHeightPropertiesChangeFromPinnedUpstreamType()throws Exception {
        var source=json(Path.of("../audit/pocket-height-source.json")).getAsJsonObject("pocketType");
        var tall=json(PACK.resolve("data/expedition_bridge/dimension_type/tall_exploration.json"));
        assertEquals(0,tall.get("min_y").getAsInt());assertEquals(512,tall.get("height").getAsInt());
        assertEquals(512,tall.get("logical_height").getAsInt());
        var compare=tall.deepCopy();compare.add("height",source.get("height"));compare.add("logical_height",source.get("logical_height"));
        assertEquals(source,compare);
    }
    @Test void packOnlyOverridesDungeonStemKeepingNativeGeneratorAndLivingTypes()throws Exception {
        var source=json(Path.of("../audit/pocket-height-source.json"));
        var dungeon=json(PACK.resolve("data/dimdoors/dimension/dungeon_pockets.json"));
        assertEquals("expedition_bridge:tall_exploration",dungeon.get("type").getAsString());
        var original=source.getAsJsonObject("dungeon");var compare=dungeon.deepCopy();compare.add("type",original.get("type"));
        assertEquals(original,compare);
        assertEquals("dimdoors:pocket",source.getAsJsonObject("public").get("type").getAsString());
        assertEquals("dimdoors:pocket",source.getAsJsonObject("personal").get("type").getAsString());
        try(var paths=Files.walk(PACK)) {
            assertEquals(Set.of("pack.mcmeta","data/dimdoors/dimension/dungeon_pockets.json",
                    "data/expedition_bridge/dimension_type/tall_exploration.json"),paths.filter(Files::isRegularFile)
                    .map(PACK::relativize).map(p->p.toString().replace('\\','/')).collect(Collectors.toSet()));
        }
    }
    @Test void tallTestSpaceRetainsWholeStartEnvelopeAndFoundationRatherThanCropping() {
        // Mandatory 163x250x163 keep_kayra start envelope; not a simulation of its complete random assembly.
        var building=new BlockBounds(0,0,0,162,249,162);
        var island=new IslandPlan(building,0,8,8,32,IslandPlan.Surface.ROCK);
        assertThrows(IllegalArgumentException.class,()->ReservationGeometry.plan(island.completeBounds(),16,0,256));
        var geometry=ReservationGeometry.plan(island.completeBounds(),16,0,512);
        var placed=geometry.translatedContent(1024,2048);
        assertEquals(island.completeBounds().height(),placed.height());
        assertEquals(314,geometry.padded().height());
        assertTrue(placed.minY()>=16);assertTrue(placed.maxY()<512-16);
        assertEquals(250,building.height());
    }
}
