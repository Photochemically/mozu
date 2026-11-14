package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

record FortProbe(ServerLevel world,TempleProbe steps) implements FortRoute.Probe {
    FortProbe(ServerLevel world){this(world,new TempleProbe(world));}
    private static BlockPos pos(Cell p){return new BlockPos(p.x(),p.y(),p.z());}
    public Type at(Cell p){return steps.at(p);}
    public boolean stair(Cell p,Direction facing){return steps.stair(p,facing);}
    public boolean feature(FortLayout.Feature feature) {
        var p=pos(feature.pos());var b=world.getBlockEntity(p);
        return BuiltInRegistries.BLOCK.getKey(world.getBlockState(p).getBlock()).toString().equals(feature.block())
                && b!=null && b.saveWithoutMetadata().getString("LootTable").equals(feature.loot());
    }
    public boolean scaffold(Cell p){return SupportedScaffold.present(world,pos(p));}
    public void addScaffold(Cell p){SupportedScaffold.place(world,pos(p));}
}
