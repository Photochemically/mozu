package dev.expedition.bridge.domain;

import java.util.*;

/** Immutable configuration snapshot pinned to one instance before allocation. No implicit tier assignments. */
public record DungeonSpec(String id,Provider provider,String target,Set<Integer> tiers,int weight,
                          Environment environment,int exits) {
    public enum Provider { WDA, NATIVE }
    public enum EntryMode {
        PERIMETER, START_ROOM, START_DECK;
        public boolean interior(){return this!=PERIMETER;}
    }
    public record Environment(int firstFreeY,int padding,int rim,int foundationDepth,IslandPlan.Surface surface,int waterDepth,boolean boarding,EntryMode entryMode) {
        public Environment(int firstFreeY,int padding,int rim,int foundationDepth,IslandPlan.Surface surface) {
            this(firstFreeY,padding,rim,foundationDepth,surface,0,false,EntryMode.PERIMETER);
        }
        public Environment(int firstFreeY,int padding,int rim,int foundationDepth,IslandPlan.Surface surface,int waterDepth) {
            this(firstFreeY,padding,rim,foundationDepth,surface,waterDepth,false,EntryMode.PERIMETER);
        }
        public Environment(int firstFreeY,int padding,int rim,int foundationDepth,IslandPlan.Surface surface,int waterDepth,boolean boarding) {
            this(firstFreeY,padding,rim,foundationDepth,surface,waterDepth,boarding,EntryMode.PERIMETER);
        }
        public Environment {
            if(firstFreeY<=-64 || firstFreeY>=320 || padding<4 || padding>64 || rim<1 || rim>64
                    || foundationDepth<4 || foundationDepth>128 || surface==null)
                throw new IllegalArgumentException("Invalid island environment");
            new TerrainLevels(firstFreeY,waterDepth);
            if((surface==IslandPlan.Surface.WATER)!=(waterDepth>0))throw new IllegalArgumentException("Explicit water depth required only for water surface");
            if(boarding && surface!=IslandPlan.Surface.WATER)throw new IllegalArgumentException("Boarding requires a water environment");
            if(entryMode==null || entryMode==EntryMode.START_ROOM && (surface!=IslandPlan.Surface.ROCK || boarding))
                throw new IllegalArgumentException("Start-room entry requires a rock environment without boarding");
            if((entryMode==EntryMode.START_DECK)!=(surface==IslandPlan.Surface.VOID))
                throw new IllegalArgumentException("Void environment and start-deck entry must be used together");
        }
    }
    public DungeonSpec {
        if(id==null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || provider==null || target==null
                || !target.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid content identity");
        tiers=Set.copyOf(tiers);
        if(tiers.isEmpty() || tiers.stream().anyMatch(t->t<1 || t>5) || weight<1 || weight>1_000_000)
            throw new IllegalArgumentException("Invalid tiers or weight");
        if(provider==Provider.WDA && (!target.startsWith("dungeons_arise:") || environment==null || exits<1 || exits>4))
            throw new IllegalArgumentException("WDA needs explicit environment and 1..4 exits");
        if(provider==Provider.NATIVE && (!target.startsWith("dimdoors:") || environment!=null || exits!=0))
            throw new IllegalArgumentException("Native room retains all original exits and has no island");
    }
}
