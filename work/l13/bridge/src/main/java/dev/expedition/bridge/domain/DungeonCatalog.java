package dev.expedition.bridge.domain;

import java.util.*;

/** Deterministic weighted choice; resource iteration order cannot change a seeded selection. */
public final class DungeonCatalog {
    public record Choice(int tier,DungeonSpec content) {
        public Choice {
            if(tier<1 || tier>5 || !content.tiers().contains(tier))throw new IllegalArgumentException("Content is not eligible for tier");
        }
    }
    private final boolean enabled;
    private final List<DungeonSpec> entries;
    private final List<Integer> unstableWeights;
    public DungeonCatalog(boolean enabled,Collection<DungeonSpec> entries,List<Integer> unstableWeights) {
        this.enabled=enabled;this.entries=entries.stream().sorted(Comparator.comparing(DungeonSpec::id)).toList();
        this.unstableWeights=List.copyOf(unstableWeights);
        if(this.entries.stream().map(DungeonSpec::id).distinct().count()!=this.entries.size())throw new IllegalArgumentException("Duplicate content ID");
        if(!unstableWeights.isEmpty() && (unstableWeights.size()!=5 || unstableWeights.stream().anyMatch(w->w<0 || w>1_000_000)
                || unstableWeights.stream().mapToLong(Integer::longValue).sum()==0))throw new IllegalArgumentException("Need five nonnegative unstable weights");
        if(enabled && unstableWeights.isEmpty())throw new IllegalArgumentException("Explicit unstable tier weights required before enabling the catalog");
        if(enabled)for(int tier=1;tier<=5;tier++)if(forTier(tier).isEmpty())throw new IllegalArgumentException("Missing candidates for tier "+tier);
    }
    public static DungeonCatalog disabled(){return new DungeonCatalog(false,List.of(),List.of());}
    public boolean enabled(){return enabled;}
    public List<DungeonSpec> entries(){return entries;}
    private List<DungeonSpec> forTier(int tier){return entries.stream().filter(e->e.tiers().contains(tier)).toList();}
    public Choice select(PortalKind kind,int fixedTier,long seed) {
        if(!enabled || kind==PortalKind.GOLD)throw new IllegalStateException("Exploration catalog unavailable for this entrance");
        var random=new Random(seed^0x504f434b45544cL);
        int tier=fixedTier;
        if(kind==PortalKind.UNSTABLE) {
            if(unstableWeights.isEmpty())throw new IllegalStateException("Unstable tier weights have not been configured");
            tier=weighted(unstableWeights,random.nextLong(unstableWeights.stream().mapToLong(Integer::longValue).sum()))+1;
        }
        var pool=forTier(tier);
        if(pool.isEmpty())throw new IllegalStateException("No eligible content for tier "+tier);
        var weights=pool.stream().map(DungeonSpec::weight).toList();
        return new Choice(tier,pool.get(weighted(weights,random.nextLong(weights.stream().mapToLong(Integer::longValue).sum()))));
    }
    static int weighted(List<Integer> weights,long sample) {
        if(sample<0)throw new IllegalArgumentException("Invalid weighted sample");
        for(int i=0;i<weights.size();i++){sample-=weights.get(i);if(sample<0)return i;}
        throw new IllegalArgumentException("Sample outside weight total");
    }
}
