package dev.expedition.bridge.content;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.dimdev.dimdoors.world.pocket.type.addon.PocketAddon;
import java.util.UUID;

/** Saved in DD's own pocket record, so stale bridge IDs cannot silently identify a different pocket. */
public final class PocketIdentity implements PocketAddon {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("expedition_bridge", "identity");
    private static final RegistrySupplier<PocketAddonType<PocketIdentity>> TYPE =
            PocketAddonType.register(ID, PocketIdentity::new, null);
    private UUID portalId;
    public PocketIdentity() {}
    public PocketIdentity(UUID portalId) { this.portalId=portalId; }
    public UUID portalId() { return portalId; }
    public static void registerType() { /* Trigger registration before registries freeze. */ }
    @Override public PocketAddon fromNbt(CompoundTag tag) { portalId=tag.getUUID("Portal"); return this; }
    @Override public CompoundTag toNbt(CompoundTag tag) { PocketAddon.super.toNbt(tag);tag.putUUID("Portal",portalId);return tag; }
    @Override public PocketAddonType<PocketIdentity> getType() { return TYPE.get(); }
    @Override public ResourceLocation getId() { return ID; }
}
