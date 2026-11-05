package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.ExpeditionBridge;
import dev.expedition.bridge.domain.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Adds an ordinary breakable gangway once, after all original pieces and before water/visitors are released. */
final class WdaBoardingJob {
    private final ServerLevel world;
    private final BoardingRouteSearch search;
    private BoardingRouteSearch.Plan route;
    private int placed;
    WdaBoardingJob(ServerLevel world,PortalRecord portal,IslandPlan island) {
        this.world=world;search=new BoardingRouteSearch(island,portal.destination().bounds(),this::probe);
    }
    boolean step(int budget,long nanos) {
        long start=System.nanoTime();int work=0;
        while(work<budget && (work==0 || System.nanoTime()-start<nanos)) {
            if(route==null) {
                search.step(1);work++;
                if(!search.complete())continue;
                route=search.result().orElseThrow(()->new IllegalStateException("No safe boarding route; original ship retained, no walls removed"));
                ExpeditionBridge.LOGGER.info("WDA boarding route: shore {}, native landing {}, {} walking cells, {} new supports",
                        route.shore(),route.landing(),route.feet().size(),route.additions().size());
            }
            if(placed<route.additions().size()) {
                var p=route.additions().get(placed);var before=probe(p);
                if(before!=BoardingRouteSearch.Block.AIR && before!=BoardingRouteSearch.Block.WATER)
                    throw new IllegalStateException("Boarding site changed; refusing to replace existing block: "+p);
                if(!world.setBlock(pos(p),Blocks.OAK_PLANKS.defaultBlockState(),18))throw new IllegalStateException("Boarding support placement failed");
                placed++;work++;continue;
            }
            // Recheck the actual built centerline in both directions; bridge cells remain ordinary player-editable blocks.
            for(int i=0;i<route.feet().size();i++) {
                var p=route.feet().get(i);
                if(probe(p)!=BoardingRouteSearch.Block.AIR || probe(p.move(0,1,0))!=BoardingRouteSearch.Block.AIR
                        || probe(p.move(0,-1,0))!=BoardingRouteSearch.Block.SUPPORT)
                    throw new IllegalStateException("Boarding route obstructed before activation: "+p);
                if(i>0) {
                    var a=route.feet().get(i-1);
                    if(a.y()!=p.y() && probe((a.y()<p.y()?a:p).move(0,2,0))!=BoardingRouteSearch.Block.AIR)
                        throw new IllegalStateException("Boarding step lacks headroom");
                }
            }
            return true;
        }
        return false;
    }
    private BoardingRouteSearch.Block probe(BoardingRouteSearch.Cell p) {
        var pos=pos(p);var state=world.getBlockState(pos);
        String slab=state.hasProperty(BlockStateProperties.SLAB_TYPE)?state.getValue(BlockStateProperties.SLAB_TYPE).getSerializedName():"";
        boolean wet=state.hasProperty(BlockStateProperties.WATERLOGGED)&&state.getValue(BlockStateProperties.WATERLOGGED);
        var block=BoardingBlockRules.classify(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),slab,wet,state.hasBlockEntity());
        if(block==BoardingRouteSearch.Block.SUPPORT && (!state.getFluidState().isEmpty() || !state.isFaceSturdy(world,pos,Direction.UP)))
            return BoardingRouteSearch.Block.BLOCKED;
        return block;
    }
    private static BlockPos pos(BoardingRouteSearch.Cell p){return new BlockPos(p.x(),p.y(),p.z());}
}
