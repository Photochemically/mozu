package dev.expedition.bridge.portal;

import dev.expedition.bridge.domain.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.Pose;
import java.util.*;
import org.dimdev.dimdoors.world.ModDimensions;

/** Checks current blocks immediately before travel. No terrain edits and no persistent chunk tickets. */
public final class SafeLanding {
    private SafeLanding() {}
    public static Optional<PortalRecord.Site> overworld(ServerPlayer player,PortalRecord.Site anchor,long seed) {
        if(!anchor.dimension().equals("minecraft:overworld"))throw new IllegalArgumentException("Not an overworld anchor");
        var world=player.server.overworld();var area=ReturnArea.around(anchor);
        var primary=searchArea(player,world,area,seed);if(primary.isPresent())return primary;
        // A verified spawn-area landing is preferable to inventing terrain or using an unsafe raw spawn position.
        var spawn=world.getSharedSpawnPos();
        for(int radius=0;radius<=4;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            var found=surface(player,world,spawn.getX()+dx,spawn.getZ()+dz);
            if(found.isPresent())return found;
        }
        return Optional.empty();
    }
    public static Optional<PortalRecord.Site> limbo(ServerPlayer player,PortalRecord.Site anchor,long seed) {
        var world=player.server.getLevel(ModDimensions.LIMBO);
        return world==null?Optional.empty():searchArea(player,world,ReturnArea.around(anchor),seed);
    }
    private static Optional<PortalRecord.Site> searchArea(ServerPlayer player,ServerLevel world,ReturnArea area,long seed) {
        int[] columns=new int[48*48];for(int i=0;i<columns.length;i++)columns[i]=i;
        var random=new Random(seed);
        for(int i=columns.length-1;i>0;i--){int j=random.nextInt(i+1),v=columns[i];columns[i]=columns[j];columns[j]=v;}
        for(int column:columns) {
            int x=area.minX()+column%48,z=area.minZ()+column/48;
            var found=surface(player,world,x,z);
            if(found.isPresent())return found;
        }
        return Optional.empty(); // Caller keeps the player in place and the same pending operation.
    }
    public static Optional<PortalRecord.Site> nearConnection(ServerPlayer player,ServerLevel world,BlockPos door,BlockBounds allowed) {
        for(int radius=0;radius<=3;radius++)for(int dy:new int[]{0,1,-1})
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
                if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
                var p=door.offset(dx,dy,dz);
                if((allowed==null || allowed.contains(p.getX(),p.getY(),p.getZ())) && safe(player,world,p))return Optional.of(site(world,p));
            }
        return Optional.empty();
    }
    private static Optional<PortalRecord.Site> surface(ServerPlayer player,ServerLevel world,int x,int z) {
        var column=new BlockPos(x,world.getMinBuildHeight(),z);
        if(!world.getWorldBorder().isWithinBounds(column))return Optional.empty();
        world.getChunk(Math.floorDiv(x,16),Math.floorDiv(z,16));
        int top=world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        for(int y=Math.min(top,world.getMaxBuildHeight()-2);y>=Math.max(world.getMinBuildHeight()+1,top-12);y--) {
            var p=new BlockPos(x,y,z);if(safe(player,world,p))return Optional.of(site(world,p));
        }
        return Optional.empty();
    }
    public static boolean safe(ServerPlayer player,ServerLevel world,BlockPos feet) {
        var standing=player.getDimensions(Pose.STANDING);
        double height=Math.max(standing.height,player.getBbHeight());
        if(feet.getY()<=world.getMinBuildHeight() || feet.getY()+height>=world.getMaxBuildHeight())return false;
        double half=Math.max(0.3,Math.max(standing.width,player.getBbWidth())/2.0);
        var box=new AABB(feet.getX()+0.5-half,feet.getY(),feet.getZ()+0.5-half,
                feet.getX()+0.5+half,feet.getY()+height,feet.getZ()+0.5+half);
        if(!world.getWorldBorder().isWithinBounds(box) || !world.noCollision(player,box))return false;
        for(int x=(int)Math.floor(box.minX);x<=(int)Math.floor(box.maxX-0.0001);x++)
            for(int z=(int)Math.floor(box.minZ);z<=(int)Math.floor(box.maxZ-0.0001);z++) {
                var floor=new BlockPos(x,feet.getY()-1,z);var state=world.getBlockState(floor);
                if(!state.isFaceSturdy(world,floor,Direction.UP) || !Block.isShapeFullBlock(state.getCollisionShape(world,floor))
                        || state.getBlock() instanceof FallingBlock || state.is(BlockTags.LEAVES) || hazardous(state)
                        || !world.getFluidState(floor).isEmpty())return false;
            }
        for(var p:BlockPos.betweenClosed(feet.offset(-1,0,-1),feet.offset(1,(int)Math.ceil(height)-1,1)))
            if(hazardous(world.getBlockState(p)) || !world.getFluidState(p).isEmpty())return false;
        return true;
    }
    private static boolean hazardous(BlockState state) {
        return state.is(BlockTags.FIRE) || state.getBlock() instanceof CampfireBlock || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL)
                || state.is(Blocks.END_GATEWAY) || state.is(Blocks.POINTED_DRIPSTONE);
    }
    public static PortalRecord.Site site(ServerLevel world,BlockPos pos) {
        return new PortalRecord.Site(world.dimension().location().toString(),pos.getX(),pos.getY(),pos.getZ());
    }
}
