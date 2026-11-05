package dev.expedition.bridge.worldgen;

import dev.expedition.bridge.domain.NaturalEntryPlan;
import net.minecraftforge.common.ForgeConfigSpec;
import java.util.*;

/** Restart-level integration switch. Geometry is pinned per save to prevent retries/config changes moving old proposals. */
public final class NaturalEntryConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.IntValue REGION,MIN,MAX,DISTANCE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> WEIGHTS;
    static {
        var b=new ForgeConfigSpec.Builder();b.push("natural_entrances");
        ENABLED=b.comment("Development gate: requires a complete enabled dungeon catalog. Restart required. No retro-generation.",
                "Also suppresses native DD gateways and WDA natural structure starts ONLY in the overworld.").define("enabled",false);
        REGION=b.comment("Layout settings are pinned on first use in each save. Use a fresh test world to tune them.").defineInRange("region_chunks",12,4,64);
        MIN=b.defineInRange("min_attempts",1,1,4);MAX=b.defineInRange("max_attempts",2,1,4);
        DISTANCE=b.defineInRange("minimum_distance_blocks",48,16,256);
        WEIGHTS=b.comment("Relative weights: cherry, oak, dark oak, warped, crimson, gold, unstable.")
                .defineList("door_weights",List.of(150,300,300,150,50,25,25),v->v instanceof Integer i && i>=0 && i<=1_000_000);
        b.pop();SPEC=b.build();
    }
    private NaturalEntryConfig(){}
    public static boolean enabled(){return ENABLED.get();}
    public static NaturalEntryPlan.Policy policy(){return new NaturalEntryPlan.Policy(REGION.get(),MIN.get(),MAX.get(),DISTANCE.get(),new ArrayList<>(WEIGHTS.get()));}
}
