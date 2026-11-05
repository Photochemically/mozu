package dev.expedition.bridge.storage;

import dev.expedition.bridge.domain.NaturalEntryPlan;
import net.minecraft.nbt.CompoundTag;
import java.util.Arrays;

public final class NaturalEntryNbt {
    private NaturalEntryNbt(){}
    public static CompoundTag write(NaturalEntryPlan.Policy policy) {
        var tag=new CompoundTag();tag.putInt("RegionChunks",policy.regionChunks());tag.putInt("MinAttempts",policy.minAttempts());
        tag.putInt("MaxAttempts",policy.maxAttempts());tag.putInt("MinDistance",policy.minDistance());
        tag.putIntArray("Weights",policy.weights());return tag;
    }
    public static NaturalEntryPlan.Policy read(CompoundTag tag) {
        return new NaturalEntryPlan.Policy(tag.getInt("RegionChunks"),tag.getInt("MinAttempts"),tag.getInt("MaxAttempts"),
                tag.getInt("MinDistance"),Arrays.stream(tag.getIntArray("Weights")).boxed().toList());
    }
}
