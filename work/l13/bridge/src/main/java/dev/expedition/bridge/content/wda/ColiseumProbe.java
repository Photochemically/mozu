package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

record ColiseumProbe(ServerLevel world,InteriorBlockProbe blocks) implements ColiseumRoute.Probe {
    ColiseumProbe(ServerLevel world){this(world,new InteriorBlockProbe(world));}
    public Type at(Cell p){return blocks.at(p);}
    public boolean device(ColiseumLayout.Device d){
        var p=new BlockPos(d.pos().x(),d.pos().y(),d.pos().z());var block=world.getBlockEntity(p);
        if(!world.getBlockState(p).is(Blocks.SPAWNER)||block==null)return false;
        var n=block.saveWithoutMetadata();return n.getCompound("SpawnData").equals(d.spawnData())&&n.getList("SpawnPotentials",Tag.TAG_COMPOUND).equals(d.potentials());
    }
}
