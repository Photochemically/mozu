package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.SlabType;

record ThornbornProbe(ServerLevel world,InteriorBlockProbe blocks) implements ThornbornRoute.Probe {
    ThornbornProbe(ServerLevel world){this(world,new InteriorBlockProbe(world));}
    private static BlockPos pos(Cell p){return new BlockPos(p.x(),p.y(),p.z());}
    public Type at(Cell p){return blocks.at(p);}
    public boolean scaffold(Cell p){return SupportedScaffold.present(world,pos(p));}
    public void addScaffold(Cell p){SupportedScaffold.place(world,pos(p));}
    public boolean bottomSlab(Cell p){var s=world.getBlockState(pos(p));return s.is(Blocks.DARK_OAK_SLAB)&&s.getValue(SlabBlock.TYPE)==SlabType.BOTTOM&&!s.getValue(SlabBlock.WATERLOGGED);}
    public boolean feature(ThornbornLayout.Feature f){var p=pos(f.pos());var e=world.getBlockEntity(p);return e!=null&&BuiltInRegistries.BLOCK.getKey(world.getBlockState(p).getBlock()).toString().equals(f.block())&&ThornbornLayout.stableData(f.block(),e.saveWithoutMetadata()).equals(f.data());}
}
