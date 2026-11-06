package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BlockBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;

record WdaTemplatePiece(String id, BlockPos origin, Rotation rotation, BlockBounds bounds, boolean terrainMatching) {
    void requireFrame(BlockPos origin, Rotation rotation, int width, int height, int depth) {
        var corner = new BlockPos(width - 1, height - 1, depth - 1).rotate(rotation).offset(origin);
        var bounds = new BlockBounds(Math.min(origin.getX(), corner.getX()), origin.getY(), Math.min(origin.getZ(), corner.getZ()),
                Math.max(origin.getX(), corner.getX()), corner.getY(), Math.max(origin.getZ(), corner.getZ()));
        if (!this.origin.equals(origin) || this.rotation != rotation || !bounds.equals(this.bounds)) {
            throw new IllegalArgumentException("Misaligned or resized original piece " + this.id);
        }
    }
}
