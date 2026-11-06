package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BlockBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;

record WdaTemplatePiece(String id, BlockPos origin, Rotation rotation, BlockBounds bounds, boolean terrainMatching) {}
