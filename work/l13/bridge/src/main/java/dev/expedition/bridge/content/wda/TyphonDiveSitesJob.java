package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

final class TyphonDiveSitesJob {
    private final TyphonDiveSearch search;
    private final String dimension;
    TyphonDiveSitesJob(ServerLevel world,TyphonDiveLayout layout,IslandPlan island,int count) {
        dimension=world.dimension().location().toString();var dry=new InteriorBlockProbe(world);
        search=new TyphonDiveSearch(layout,island,count,new TyphonDiveSearch.Probe() {
            private BlockPos pos(Cell p){return new BlockPos(p.x(),p.y(),p.z());}
            public InteriorBlockRules.Type at(Cell p){return dry.at(p);}
            public boolean water(Cell p){var s=world.getBlockState(pos(p));return s.is(Blocks.WATER)&&s.getFluidState().isSource();}
            public boolean magma(Cell p){return world.getBlockState(pos(p)).is(Blocks.MAGMA_BLOCK);}
            public boolean chest(Cell p){return world.getBlockState(pos(p)).is(Blocks.CHEST)&&world.getBlockEntity(pos(p))!=null;}
            public boolean stone(Cell p){return world.getBlockState(pos(p)).is(Blocks.STONE);}
        });
    }
    boolean step(int budget,long nanos) {
        if(budget<=0||nanos<=0)throw new IllegalArgumentException("Invalid dive work budget");
        long started=System.nanoTime();int n=0;
        while(!search.complete()&&n<budget&&(n==0||System.nanoTime()-started<nanos)){search.step(1);n++;}
        if(search.complete()&&search.result().isEmpty())throw new IllegalStateException("Original seabed, water approach or dry shore obstructed; refusing bypass or terrain edits");
        return search.complete();
    }
    List<PortalRecord.Site> sites(){if(!search.complete()||search.result().isEmpty())throw new IllegalStateException("Dive sites incomplete");return search.result().stream().map(p->new PortalRecord.Site(dimension,p.x(),p.y(),p.z())).toList();}
}
