package dev.expedition.bridge.content;

import dev.expedition.bridge.domain.PortalRecord;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Providers validate inputs before changing the world. Never regenerate a saved destination. */
public interface PocketContentProvider {
    ResourceLocation id();
    void validate(MinecraftServer server, PortalRecord portal);
    PortalRecord.Destination generate(MinecraftServer server, PortalRecord portal);
}
