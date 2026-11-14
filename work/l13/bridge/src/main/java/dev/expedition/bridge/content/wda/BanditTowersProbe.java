package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

record BanditTowersProbe(ServerLevel world,InteriorBlockProbe blocks) implements BanditTowersRoute.Probe {
    BanditTowersProbe(ServerLevel world){this(world,new InteriorBlockProbe(world));}
    private static BlockPos pos(Cell p){return new BlockPos(p.x(),p.y(),p.z());}
    public Type at(Cell p){return blocks.at(p);}
    public boolean scaffold(Cell p){return SupportedScaffold.present(world,pos(p));}
    public void addScaffold(Cell p){SupportedScaffold.place(world,pos(p));}
    public boolean feature(BanditTowersLayout.Feature f){
        var p=pos(f.pos());var entity=world.getBlockEntity(p);
        if(entity==null||!BuiltInRegistries.BLOCK.getKey(world.getBlockState(p).getBlock()).toString().equals(f.block()))return false;
        var n=entity.saveWithoutMetadata();return n.getString("LootTable").equals(f.loot())
                &&n.getCompound("SpawnData").equals(f.spawnData())&&n.getList("SpawnPotentials",Tag.TAG_COMPOUND).equals(f.spawnPotentials());
    }
}
