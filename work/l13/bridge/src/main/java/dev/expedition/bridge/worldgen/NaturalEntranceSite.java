package dev.expedition.bridge.worldgen;

import net.minecraft.core.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.Optional;

/** Conservative terrain-only check, identical for all seven types. Never clears vegetation or edits a landing platform. */
public final class NaturalEntranceSite {
    private NaturalEntranceSite(){}
    public static Optional<BlockPos> find(ServerLevel level,int x,int z) {
        if(level.getChunkSource().getChunkNow(Math.floorDiv(x,16),Math.floorDiv(z,16))==null)return Optional.empty();
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        var pos=new BlockPos(x,y,z);return safe(level,pos)?Optional.of(pos):Optional.empty();
    }
    public static boolean safe(ServerLevel level,BlockPos pos) {
        if(pos.getY()<=level.getMinBuildHeight() || pos.getY()+2>=level.getMaxBuildHeight())return false;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            var p=pos.offset(dx,0,dz);
            if(!level.getWorldBorder().isWithinBounds(p) || level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)==null)return false;
            var floor=level.getBlockState(p.below());
            if(!floor.isFaceSturdy(level,p.below(),Direction.UP) || !floor.isCollisionShapeFullBlock(level,p.below())
                    || !floor.getFluidState().isEmpty() || floor.getBlock() instanceof FallingBlock || floor.is(BlockTags.LEAVES)
                    || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS) || floor.is(Blocks.POWDER_SNOW)
                    || floor.is(Blocks.CAMPFIRE) || floor.is(Blocks.SOUL_CAMPFIRE))return false;
            for(int dy=0;dy<3;dy++)if(!level.getBlockState(p.above(dy)).isAir())return false;
        }
        // Existing structure references in this already-loaded chunk make the site ineligible, avoiding entrances on roofs or machinery.
        return level.structureManager().getAllStructuresAt(pos).isEmpty();
    }
}
