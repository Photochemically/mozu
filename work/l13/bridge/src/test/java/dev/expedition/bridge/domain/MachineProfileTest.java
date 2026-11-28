package dev.expedition.bridge.domain;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Bundled heavy machine profiles must pin the original templates exactly and never trust the file. */
class MachineProfileTest {
    private static MachineProfile load(String structureId) {
        var path = "/data/expedition_bridge/machine_profiles/" + structureId.replace('/', '_') + ".json";
        try (var stream = MachineProfileTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "bundled profile missing: " + path);
            return MachineProfile.fromJson(structureId, JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }
    private static MachineProfile jungle() { return load("dungeon/jungle_riddle_fixed"); }
    private static MachineProfile telecom() { return load("lab/telecommunication"); }

    @Test void bundledProfilesPinTemplateFingerprintsAndGeometry() {
        var jungle = jungle();
        assertEquals("1cce95257dccd788ae60b787eaf4bd53667d83c9b05ce6ebc0ff17b54114e7de", jungle.templateSha256());
        assertEquals(List.of(44, 42, 54), jungle.size());
        assertEquals(720, jungle.stableCount());
        assertEquals(49, jungle.pistonBaseCount());
        var telecom = telecom();
        assertEquals("bbb881b9fc18d43f830a8b79d0246cc51d74d7f5d7f80236d0146edc599b51e6", telecom.templateSha256());
        assertEquals(List.of(31, 31, 57), telecom.size());
        assertEquals(385, telecom.stableCount());
        assertEquals(48, telecom.pistonBaseCount());
    }

    @Test void verificationAcceptsExactGeometryAfterTranslation() {
        var profile = jungle();
        var offset = new BlockPos(1000, 60, -400);
        var scanned = new LinkedHashSet<MachineProfile.Component>();
        for (var component : profile.stableComponents())
            scanned.add(new MachineProfile.Component(component.type(), component.facing(), component.pos().offset(offset)));
        var verified = profile.verifyComponents(scanned);
        assertEquals(profile.stableCount(), verified.size());
        var anchor = profile.stableComponents().iterator().next().pos().offset(offset);
        assertTrue(verified.contains(anchor));
    }

    @Test void verificationIgnoresVolatileCellsButStillRequiresAllStableOnes() {
        var profile = telecom();
        var scanned = new LinkedHashSet<MachineProfile.Component>();
        for (var component : profile.stableComponents()) scanned.add(component);
        scanned.add(MachineProfile.Component.of("minecraft:piston_head", Direction.NORTH, 5, 1, 5));
        scanned.add(MachineProfile.Component.of("minecraft:moving_piston", Direction.EAST, 6, 2, 6));
        assertEquals(profile.stableCount(), profile.verifyComponents(scanned).size());
        var missing = new LinkedHashSet<>(scanned);
        missing.remove(missing.iterator().next());
        assertThrows(IllegalArgumentException.class, () -> profile.verifyComponents(missing));
    }

    @Test void verificationRejectsTamperedAndExtraComponents() {
        var profile = jungle();
        var scanned = new LinkedHashSet<MachineProfile.Component>(profile.stableComponents());
        var tampered = new LinkedHashSet<MachineProfile.Component>();
        var replaced = false;
        for (var component : scanned) {
            if (!replaced && component.type().equals("minecraft:redstone_wire")) {
                tampered.add(new MachineProfile.Component("minecraft:redstone_torch", component.facing(), component.pos()));
                replaced = true;
            } else tampered.add(component);
        }
        assertTrue(replaced);
        assertThrows(IllegalArgumentException.class, () -> profile.verifyComponents(tampered));
        var extra = new LinkedHashSet<MachineProfile.Component>(scanned);
        extra.add(MachineProfile.Component.of("minecraft:repeater", Direction.NORTH, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> profile.verifyComponents(extra));
    }

    @Test void motionNeedsAPinnedPistonBaseAndOnlyTouchesMachineComponents() {
        var profile = jungle();
        var base = profile.pistonBases().keySet().iterator().next();
        var facing = profile.pistonBases().get(base);
        var head = base.relative(facing);
        var payload = head.relative(facing);
        assertTrue(profile.permitsMotion(base, facing, Set.of(base, head, payload),
                pos -> true, pos -> true));
        assertTrue(profile.permitsMotion(base, facing, Set.of(base, head, payload),
                pos -> pos.equals(base) || pos.equals(head), pos -> true), "unprotected corridor cells stay irrelevant");
        assertFalse(profile.permitsMotion(base, facing, Set.of(base, head, payload),
                pos -> true, pos -> !pos.equals(payload)), "a protected non-component cell blocks the motion");
        assertFalse(profile.permitsMotion(base, facing.getOpposite(), Set.of(base, head),
                pos -> true, pos -> true), "facing must match the pinned piston");
        assertFalse(profile.permitsMotion(base.offset(5, 0, 0), facing, Set.of(base, head),
                pos -> true, pos -> true), "foreign pistons never get a window");
        var telecom = telecom();
        var tBase = telecom.pistonBases().keySet().iterator().next();
        var tFacing = telecom.pistonBases().get(tBase);
        assertTrue(telecom.permitsMotion(tBase, tFacing, Set.of(tBase, tBase.relative(tFacing)),
                pos -> true, pos -> true));
        assertFalse(telecom.permitsMotion(tBase, tFacing, Set.of(tBase, tBase.relative(tFacing)),
                pos -> true, pos -> !pos.equals(tBase)), "protected rift/route cells are never components");
    }

    @Test void profilesRejectMalformedResources() {
        assertThrows(IllegalArgumentException.class, () -> MachineProfile.fromJson("x/y", new com.google.gson.JsonObject()));
    }
    @Test void translatedMachineUsesWorldFootprintWithPinnedLocalPiston() throws Exception {
        var profile = load("dungeon/jungle_riddle_fixed");
        var piston = profile.pistonBases().entrySet().iterator().next();
        var offset = new BlockPos(1000, 60, -400);
        var base = piston.getKey().offset(offset);
        var head = base.relative(piston.getValue());
        assertTrue(profile.permitsWorldMotion(offset,base,piston.getValue(),Set.of(base,head),head::equals,head::equals));
        assertFalse(profile.permitsWorldMotion(BlockPos.ZERO,base,piston.getValue(),Set.of(base,head),head::equals,head::equals));
    }
}
