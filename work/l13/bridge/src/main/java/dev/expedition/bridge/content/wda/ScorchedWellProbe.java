package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;

final class ScorchedWellProbe implements ScorchedWellSiteSearch.Probe {
    private final ServerLevel world;private final InteriorBlockProbe blocks;
    ScorchedWellProbe(ServerLevel world){this.world=world;blocks=new InteriorBlockProbe(world);}
    public InteriorBlockRules.Type at(Cell p){return blocks.at(p);}
    public boolean lava(Cell p){return world.getFluidState(new BlockPos(p.x(),p.y(),p.z())).is(FluidTags.LAVA);}
}
