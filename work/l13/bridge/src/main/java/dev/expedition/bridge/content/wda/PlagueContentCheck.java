package dev.expedition.bridge.content.wda;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import java.util.function.Predicate;

/** One original container, trap, water source or encounter per work unit, before selecting interior doors. */
final class PlagueContentCheck {
    private final PlagueLayout layout;
    private final Predicate<PlagueLayout.Feature> probe;
    private int checked;
    PlagueContentCheck(PlagueLayout layout,Predicate<PlagueLayout.Feature> probe){this.layout=layout;this.probe=probe;}
    PlagueContentCheck(PlagueLayout layout,ServerLevel world){this(layout,f->{
        var p=new BlockPos(f.pos().x(),f.pos().y(),f.pos().z());var state=world.getBlockState(p);
        if(!BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(f.block()))return false;
        CompoundTag n;
        if(state.is(Blocks.WATER)){n=new CompoundTag();n.putInt("level",state.getValue(LiquidBlock.LEVEL));}
        else {var entity=world.getBlockEntity(p);if(entity==null)return false;n=entity.saveWithoutMetadata();}
        return PlagueLayout.stableData(f.block(),n).equals(f.data());
    });}
    boolean step(){
        if(checked==layout.features().size())return true;
        var f=layout.features().get(checked++);
        if(!probe.test(f))throw new IllegalStateException("Changed original plague asylum content at "+f.pos());
        return false;
    }
}
