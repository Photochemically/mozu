package dev.expedition.bridge.content;

import dev.expedition.bridge.domain.BlockBounds;
import dev.expedition.bridge.domain.PortalKind;
import dev.expedition.bridge.domain.PortalRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.dimdev.dimdoors.api.util.Location;
import org.dimdev.dimdoors.api.util.Path;
import org.dimdev.dimdoors.pockets.PocketGenerator;
import org.dimdev.dimdoors.pockets.PocketLoader;
import org.dimdev.dimdoors.pockets.virtual.reference.IdReference;
import org.dimdev.dimdoors.rift.targets.GlobalReference;
import org.dimdev.dimdoors.world.ModDimensions;
import org.dimdev.dimdoors.world.level.registry.DimensionalRegistry;
import org.dimdev.dimdoors.world.pocket.VirtualLocation;

/** References the installed native treasure room. No copied template, replaced loot, or public pocket. */
public final class NativeRewardRoomProvider implements PocketContentProvider {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("expedition_bridge", "native_reward");
    public static final ResourceLocation ROUTE = ResourceLocation.fromNamespaceAndPath("expedition_bridge", "reward/native_treasure");
    public static final ResourceLocation TEMPLATE = ResourceLocation.fromNamespaceAndPath("dimdoors", "dungeon/upwards_t_treasure");

    @Override public ResourceLocation id() { return ID; }

    @Override public void validate(MinecraftServer server, PortalRecord portal) {
        if (!server.isSameThread()) throw new IllegalStateException("Native pocket generation requires the server thread");
        if (portal.kind() != PortalKind.GOLD) throw new IllegalArgumentException("Reward provider requires a gold entrance");
        var world = dungeonWorld(server);
        var loader = PocketLoader.getInstance();
        var template = loader.getTemplates().get(Path.stringPath(TEMPLATE));
        if (template == null || loader.getGenerator(TEMPLATE) == null || loader.getVirtual(ROUTE) == null)
            throw new IllegalStateException("Native treasure room unavailable; enable the Dimensional Doors default dungeon pack. No substitute public pocket will be created.");
        if(!(loader.getVirtual(ROUTE) instanceof IdReference reference))throw new IllegalArgumentException("Reward route must remain a direct native room");
        NativeGenerationGuard.validatedDefinition(TEMPLATE,reference);
        var schematic = template.getSchematic();
        if (schematic.getWidth() <= 0 || schematic.getLength() <= 0 || schematic.getHeight() <= 0
                || schematic.getHeight() > world.getHeight())
            throw new IllegalStateException("Native reward schematic has invalid or unsupported dimensions");
        if (server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(portal.entrance().dimension()))) == null)
            throw new IllegalStateException("Entrance dimension unavailable");
    }

    @Override public PortalRecord.Destination generate(MinecraftServer server, PortalRecord portal) {
        validate(server, portal);
        if (portal.phase() != PortalRecord.Phase.PREPARING)
            throw new IllegalStateException("Reward creation must be journaled before native allocation");
        var entrance = portal.entrance();
        var location = new Location(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(entrance.dimension())),
                new BlockPos(entrance.x(), entrance.y(), entrance.z()));
        var current = VirtualLocation.fromLocation(location);
        var virtual = new VirtualLocation(current.getWorld(), current.getX(), current.getZ(), current.getDepth() + 1);
        // Generate the original building/loot. NativeExitInstaller replaces its rift targets before activation.
        try(var guard=NativeGenerationGuard.open(server,portal,TEMPLATE)) {
            var pocket = PocketGenerator.generateFromVirtualPocket(dungeonWorld(server), ROUTE, virtual, new GlobalReference(location), null);
            var arrival = DimensionalRegistry.getRiftRegistry().getPocketEntrance(pocket);
            if (arrival == null) throw new IllegalStateException("Native reward room has no registered return entrance; reservation retained");
            return guard.finish(pocket,new PortalRecord.Site(arrival.world.location().toString(), arrival.pos.getX(), arrival.pos.getY(), arrival.pos.getZ()));
        }
    }

    private ServerLevel dungeonWorld(MinecraftServer server) {
        var world = server.getLevel(ModDimensions.DUNGEON);
        if (world == null) throw new IllegalStateException("Dimensional Doors dungeon dimension unavailable");
        return world;
    }
}
