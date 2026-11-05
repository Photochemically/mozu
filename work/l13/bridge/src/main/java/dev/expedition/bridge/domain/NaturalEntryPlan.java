package dev.expedition.bridge.domain;

import java.util.*;

/** Pure deterministic proposals; spacing is resolved without loading neighboring chunks. */
public final class NaturalEntryPlan {
    public record Policy(int regionChunks,int minAttempts,int maxAttempts,int minDistance,List<Integer> weights) {
        public Policy {
            weights=List.copyOf(weights);
            if(regionChunks<4 || regionChunks>64 || minAttempts<1 || maxAttempts<minAttempts || maxAttempts>4
                    || minDistance<16 || minDistance>256 || weights.size()!=7
                    || weights.stream().anyMatch(w->w<0 || w>1_000_000) || weights.stream().mapToLong(Integer::longValue).sum()==0)
                throw new IllegalArgumentException("Invalid natural entrance policy");
        }
        public static Policy defaults(){return new Policy(12,1,2,48,List.of(150,300,300,150,50,25,25));}
    }
    public enum Type {
        CHERRY(PortalKind.EXPLORATION,1,"minecraft_cherry"), OAK(PortalKind.EXPLORATION,2,"minecraft_oak"),
        DARK_OAK(PortalKind.EXPLORATION,3,"minecraft_dark_oak"), WARPED(PortalKind.EXPLORATION,4,"minecraft_warped"),
        CRIMSON(PortalKind.EXPLORATION,5,"minecraft_crimson"), GOLD(PortalKind.GOLD,0,"dimdoors_gold"),
        UNSTABLE(PortalKind.UNSTABLE,0,"minecraft_bamboo");
        public final PortalKind kind;public final int tier;public final String block;
        Type(PortalKind kind,int tier,String wood){this.kind=kind;this.tier=tier;this.block="dimdoors:block_ag_dim_"+wood+"_door";}
    }
    public record Candidate(int regionX,int regionZ,int slot,int x,int z,long priority,long contentSeed,Type type,int facing,UUID id) {
        public int chunkX(){return Math.floorDiv(x,16);} public int chunkZ(){return Math.floorDiv(z,16);}
    }
    private final long seed;private final Policy policy;
    public NaturalEntryPlan(long seed,Policy policy){this.seed=seed;this.policy=Objects.requireNonNull(policy);}
    public List<Candidate> region(int x,int z) {
        long cell=mix(seed^mix(x)^Long.rotateLeft(mix(z),32)^0x454e545259L);
        var random=new Random(cell);int count=policy.minAttempts()+random.nextInt(policy.maxAttempts()-policy.minAttempts()+1);
        var out=new ArrayList<Candidate>();
        for(int i=0;i<count;i++) {
            // Keep the entire safety footprint inside one chunk, without favoring any door type.
            int px=Math.toIntExact(((long)x*policy.regionChunks()+random.nextInt(policy.regionChunks()))*16+2+random.nextInt(12));
            int pz=Math.toIntExact(((long)z*policy.regionChunks()+random.nextInt(policy.regionChunks()))*16+2+random.nextInt(12));
            long key=mix(cell^mix(i+1));var typeRandom=new Random(mix(key^0x54595045L));
            Type type=choose(policy.weights(),typeRandom.nextLong(policy.weights().stream().mapToLong(Integer::longValue).sum()));
            out.add(new Candidate(x,z,i,px,pz,mix(key^0x5052494fL),mix(key^0x434f4e54454e54L),type,
                    (int)(mix(key^0x46414345L)&3),new UUID(mix(key^0x5555494431L),mix(key^0x5555494432L))));
        }
        return List.copyOf(out);
    }
    public List<Candidate> chunk(int x,int z) {
        return region(Math.floorDiv(x,policy.regionChunks()),Math.floorDiv(z,policy.regionChunks())).stream()
                .filter(c->c.chunkX()==x && c.chunkZ()==z).toList();
    }
    public boolean survivesSpacing(Candidate candidate) {
        int reach=(policy.minDistance()+policy.regionChunks()*16-1)/(policy.regionChunks()*16);
        for(int dx=-reach;dx<=reach;dx++)for(int dz=-reach;dz<=reach;dz++)
            for(var other:region(candidate.regionX()+dx,candidate.regionZ()+dz)) {
                if(other.id().equals(candidate.id()))continue;
                if(tooClose(candidate.x(),candidate.z(),other.x(),other.z(),policy.minDistance()) && precedes(other,candidate))return false;
            }
        return true;
    }
    private static boolean precedes(Candidate a,Candidate b) {
        int compare=Long.compareUnsigned(a.priority(),b.priority());
        if(compare!=0)return compare<0;
        if(a.regionX()!=b.regionX())return a.regionX()<b.regionX();
        if(a.regionZ()!=b.regionZ())return a.regionZ()<b.regionZ();
        return a.slot()<b.slot();
    }
    public static boolean tooClose(int x,int z,int otherX,int otherZ,int distance) {
        long dx=(long)x-otherX,dz=(long)z-otherZ;
        return Math.abs(dx)<distance && Math.abs(dz)<distance && dx*dx+dz*dz<(long)distance*distance;
    }
    static Type choose(List<Integer> weights,long sample) {
        if(sample<0)throw new IllegalArgumentException("Negative weight sample");
        for(int i=0;i<weights.size();i++){sample-=weights.get(i);if(sample<0)return Type.values()[i];}
        throw new IllegalArgumentException("Sample outside weights");
    }
    private static long mix(long value) {
        value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;value=(value^(value>>>27))*0x94d049bb133111ebL;return value^(value>>>31);
    }
}
