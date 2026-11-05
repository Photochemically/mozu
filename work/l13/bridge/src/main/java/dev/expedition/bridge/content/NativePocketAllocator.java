package dev.expedition.bridge.content;

import dev.expedition.bridge.domain.BlockBounds;
import dev.expedition.bridge.domain.ReservationGeometry;
import dev.expedition.bridge.domain.NativeReservation;
import dev.expedition.bridge.storage.ExpeditionSavedData;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import org.dimdev.dimdoors.world.ModDimensions;
import org.dimdev.dimdoors.world.level.registry.DimensionalRegistry;
import org.dimdev.dimdoors.world.pocket.VirtualLocation;
import org.dimdev.dimdoors.world.pocket.type.Pocket;

/** Allocates full content envelopes; does not write blocks or force-load chunks. */
public final class NativePocketAllocator {
    public record Reservation(Pocket pocket, BlockBounds allocation, BlockBounds content) {}

    public Reservation reserve(ServerLevel world, BlockBounds completeContent, int margin, VirtualLocation virtual) {
        if (!world.getServer().isSameThread()) throw new IllegalStateException("Allocation off server thread");
        if (!world.dimension().equals(ModDimensions.DUNGEON))
            throw new IllegalArgumentException("Exploration allocation cannot use player living dimensions");
        var geometry = ReservationGeometry.plan(completeContent, margin, world.getMinBuildHeight(), world.getMaxBuildHeight());
        var padded = geometry.padded();
        // PocketBuilder's upper corner is origin + size, so expand by span - 1, but reserve all cells.
        var builder = Pocket.builder()
                .expand(new Vec3i(padded.width() - 1, padded.height() - 1, padded.depth() - 1))
                .expandExpected(new Vec3i(1, 1, 1))
                .offsetOrigin(new Vec3i(0, geometry.minBuildY(), 0))
                .virtualLocation(virtual);
        var pocket = DimensionalRegistry.getPocketDirectory(world.dimension()).newPocket(builder);
        var box = pocket.getBox();
        var allocated = new BlockBounds(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
        var actualContent = geometry.translatedContent(box.minX(), box.minZ());
        if (!allocated.contains(actualContent) || allocated.width() != padded.width() || allocated.height() != padded.height()
                || allocated.depth() != padded.depth())
            throw new IllegalStateException("Native allocation bounds mismatch; reserved space retained");
        // The DD capability and our journal are different files. A crash can leave the native allocator behind.
        // Retain the new native reservation but never write into another journaled instance, including failed ones.
        NativeReservation.validate(world.dimension().location().toString(),pocket.getId(),allocated,
                world.getMinBuildHeight(),world.getMaxBuildHeight(),ExpeditionSavedData.get(world.getServer()).records());
        return new Reservation(pocket, allocated, actualContent);
    }
}
