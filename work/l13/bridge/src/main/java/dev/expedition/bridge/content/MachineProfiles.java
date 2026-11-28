package dev.expedition.bridge.content;

import com.google.gson.JsonParser;
import dev.expedition.bridge.domain.MachineProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of bundled heavy machine profiles. Loaded lazily from the bridge's own resources
 * through the vanilla resource manager; every profile is re-validated by {@link MachineProfile#fromJson}
 * and was generated from the pinned dimdoors jar by the committed audit tool.
 */
public final class MachineProfiles {
    private static final List<String> STRUCTURE_IDS = List.of("dungeon/jungle_riddle_fixed", "lab/telecommunication");
    private static final Map<String, MachineProfile> CACHE = new ConcurrentHashMap<>();

    private MachineProfiles() {}

    public static List<String> structureIds() { return STRUCTURE_IDS; }

    public static MachineProfile forStructure(MinecraftServer server, String structureId) {
        if (structureId == null || !STRUCTURE_IDS.contains(structureId)) return null;
        return CACHE.computeIfAbsent(structureId, id -> {
            var location = ResourceLocation.parse("expedition_bridge:machine_profiles/" + id.replace('/', '_'));
            try (var stream = server.getResourceManager().getResource(location)
                    .orElseThrow(() -> new IllegalStateException("Bundled machine profile missing: " + location))
                    .open()) {
                return MachineProfile.fromJson(id, JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (IOException failure) {
                throw new UncheckedIOException("Cannot read bundled machine profile " + id, failure);
            }
        });
    }
}
