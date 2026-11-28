package dev.expedition.bridge.domain;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;

/** One permanent identity per entrance; data survives even when a special entrance closes. */
public final class PortalRecord {
    public enum Phase { NEW, PREPARING, RESERVED, GENERATING, READY, FAILED, CLOSING, CLOSED }
    public record Site(String dimension, int x, int y, int z) {
        public Site { Objects.requireNonNull(dimension); }
        public String key() { return dimension + ":" + x + ":" + y + ":" + z; }
    }
    public record Destination(String dimension, int nativePocketId, BlockBounds bounds, Site arrival) {
        public Destination {
            Objects.requireNonNull(dimension); Objects.requireNonNull(bounds); Objects.requireNonNull(arrival);
            if (!dimension.equals(arrival.dimension()) || !bounds.contains(arrival.x(),arrival.y(),arrival.z()))
                throw new IllegalArgumentException("Arrival outside reserved space");
        }
    }

    private final UUID id;
    private final PortalKind kind;
    private final int tier;
    private final Site entrance;
    private final long seed;
    private final VisitLedger visits;
    private final Set<String> defeatedBosses;
    private final Map<UUID,Site> returnAnchors=new LinkedHashMap<>();
    private final Set<UUID> livingVisitors=new LinkedHashSet<>();
    private final Set<Site> exits=new LinkedHashSet<>();
    private final Map<Site,Site> internalLinks=new LinkedHashMap<>();
    private final Set<Site> protectedBlocks=new LinkedHashSet<>();
    public static final int MAX_PROTECTED_BLOCKS=4096;
    /** Bounded automatic retries for failures that provably happened before allocation or world writes. */
    public static final int MAX_GENERATION_ATTEMPTS=3;
    private Phase phase;
    private int generationAttempts;
    private Site machineOrigin;
    private Destination destination;
    private String structureId;
    private String failure;
    private DungeonCatalog.Choice selection;

    public PortalRecord(UUID id, PortalKind kind, int tier, Site entrance, long seed) {
        this(id,kind,tier,entrance,seed,Phase.NEW,null,"",new VisitLedger(),Set.of(),"");
    }
    public PortalRecord(UUID id, PortalKind kind, int tier, Site entrance, long seed, Phase phase,
                        Destination destination, String structureId, VisitLedger visits, Set<String> defeatedBosses, String failure) {
        this.id=Objects.requireNonNull(id); this.kind=Objects.requireNonNull(kind); kind.validateTier(tier); this.tier=tier;
        this.entrance=Objects.requireNonNull(entrance); this.seed=seed; this.phase=Objects.requireNonNull(phase);
        this.destination=destination; this.structureId=Objects.requireNonNull(structureId);
        this.visits=Objects.requireNonNull(visits); this.defeatedBosses=new LinkedHashSet<>(defeatedBosses);
        this.failure=Objects.requireNonNull(failure);this.generationAttempts=0;
        if ((phase==Phase.RESERVED || phase==Phase.GENERATING || phase==Phase.READY) && destination==null)
            throw new IllegalArgumentException("Active portal is missing a destination");
    }
    public UUID id() { return id; }
    public PortalKind kind() { return kind; }
    public int tier() { return tier; }
    public Site entrance() { return entrance; }
    public long seed() { return seed; }
    public Phase phase() { return phase; }
    public int generationAttempts() { return generationAttempts; }
    public void restoreAttempts(int attempts) {
        if(attempts<0)throw new IllegalArgumentException("Invalid saved generation attempts");
        this.generationAttempts=attempts;
    }
    /** Every retry attempt counts fresh; clears the previous attempt's diagnostic before starting again. */
    public void beginRetryAttempt() {
        if(phase!=Phase.NEW || destination!=null)throw new IllegalStateException("No retryable generation attempt available");
        failure="";
    }
    /**
     * Failure before allocation or world writes. Keeps the pinned content, seed and journey outcomes;
     * bounded by {@link #MAX_GENERATION_ATTEMPTS}, after which the instance quarantines for admin review.
     */
    public void retryableFailure(String reason) {
        if(phase!=Phase.NEW && phase!=Phase.PREPARING)throw new IllegalStateException("Cannot retry generation from "+phase);
        if(destination!=null)throw new IllegalStateException("Allocated generation cannot be retried automatically");
        failure=Objects.requireNonNull(reason);generationAttempts++;
        if(generationAttempts>=MAX_GENERATION_ATTEMPTS)phase=Phase.FAILED;
        else phase=Phase.NEW;
    }
    /** Admin reset for an unallocated failure only; allocated instances must use {@link #adminReassign()}. */
    public void adminRetry() {
        if(phase!=Phase.FAILED)throw new IllegalStateException("Only failed entrances can be retried");
        if(destination!=null || generationAttempts!=MAX_GENERATION_ATTEMPTS)throw new IllegalStateException("Only proven pre-allocation retry exhaustion can be reopened");
        generationAttempts=0;failure="";structureId="";phase=Phase.NEW;
    }
    /**
     * Admin recovery for an allocated or partially built failure. Drops only generation artifacts and keeps
     * the pinned content, seed, visits, anchors and boss rewards; the old space is never deleted or rewritten.
     * The next entry allocates a new independent reservation and re-verifies the entrance.
     */
    public void adminReassign() {
        if(phase!=Phase.FAILED)throw new IllegalStateException("Only failed entrances can be reassigned");
        if(destination==null)throw new IllegalStateException("No reservation to reassign; use adminRetry");
        destination=null;machineOrigin=null;exits.clear();internalLinks.clear();protectedBlocks.clear();
        generationAttempts=0;failure="";structureId="";phase=Phase.NEW;
    }
    public Site machineOrigin() { return machineOrigin; }
    public void restoreMachineOrigin(Site origin) {
        Objects.requireNonNull(origin);
        if(destination==null || machineOrigin!=null || !destination.dimension().equals(origin.dimension())
                || !destination.bounds().contains(origin.x(),origin.y(),origin.z()))
            throw new IllegalArgumentException("Invalid machine origin");
        machineOrigin=origin;
    }
    public Destination destination() { return destination; }
    public String structureId() { return structureId; }
    public String failure() { return failure; }
    public DungeonCatalog.Choice selection(){return selection;}
    public int effectiveTier(){return selection==null?tier:selection.tier();}
    public void selectContent(DungeonCatalog.Choice choice) {
        if(kind==PortalKind.GOLD || (kind==PortalKind.EXPLORATION && tier!=choice.tier()))throw new IllegalArgumentException("Content tier does not match entrance");
        if(selection!=null && !selection.equals(choice))throw new IllegalStateException("Instance content is already pinned");
        if(selection==null && phase!=Phase.NEW)throw new IllegalStateException("Cannot assign content after generation started");
        selection=Objects.requireNonNull(choice);
    }
    public void restoreSelection(DungeonCatalog.Choice choice) {
        if(selection!=null || kind==PortalKind.GOLD || (kind==PortalKind.EXPLORATION && tier!=choice.tier()))
            throw new IllegalArgumentException("Invalid saved content selection");
        selection=Objects.requireNonNull(choice);
    }
    public VisitLedger visits() { return visits; }
    public Set<String> defeatedBosses() { return Set.copyOf(defeatedBosses); }
    public Map<UUID,Site> returnAnchors() { return Map.copyOf(returnAnchors); }
    public Site returnAnchor(UUID player) { return returnAnchors.get(player); }
    public void bindReturnAnchor(UUID player,Site site) {
        if(!site.dimension().equals("minecraft:overworld"))throw new IllegalArgumentException("Invalid overworld anchor");
        returnAnchors.put(Objects.requireNonNull(player),site);
    }
    public Set<UUID> livingVisitors(){return Set.copyOf(livingVisitors);}
    public void livingVisit(UUID player,boolean visiting) {
        if(!returnAnchors.containsKey(player))throw new IllegalStateException("Living-space visit without exploration context");
        if(visiting)livingVisitors.add(player);else livingVisitors.remove(player);
    }
    public Set<Site> exits(){return Set.copyOf(exits);}
    public Map<Site,Site> internalLinks(){return Map.copyOf(internalLinks);}
    public void registerInternalLink(Site source,Site target) {
        if(destination==null || kind==PortalKind.GOLD || !NativeRiftPlan.inside(destination,source)
                || !NativeRiftPlan.inside(destination,target) || source.equals(target) || source.equals(destination.arrival()) || exits.contains(source))
            throw new IllegalArgumentException("Invalid native internal link");
        if(internalLinks.putIfAbsent(source,target)!=null)throw new IllegalArgumentException("Duplicate native internal link");
    }
    public boolean isInternalDevice(Site site) {return internalLinks.containsKey(site)||internalLinks.containsValue(site);}
    public Set<Site> protectedBlocks(){return Set.copyOf(protectedBlocks);}
    public boolean isProtectedBlock(Site site){return protectedBlocks.contains(site);}
    /** Validate the whole batch before recording any position. Never grows from later player construction. */
    public void installProtectedBlocks(java.util.Collection<Site> sites) {
        require(Phase.GENERATING);restoreProtectedBlocks(sites);
    }
    public void restoreProtectedBlocks(java.util.Collection<Site> sites) {
        if(!protectedBlocks.isEmpty() || sites.size()>MAX_PROTECTED_BLOCKS)
            throw new IllegalArgumentException("Invalid protected block batch");
        var validated=new LinkedHashSet<Site>();
        for(var site:sites) {
            if(site==null || destination==null || !NativeRiftPlan.inside(destination,site) || !validated.add(site))
                throw new IllegalArgumentException("Duplicate or out-of-instance protected block");
        }
        protectedBlocks.addAll(validated);
    }
    public void registerExit(Site site) {
        if(destination==null || !site.dimension().equals(destination.dimension()) || !destination.bounds().contains(site.x(),site.y(),site.z()))
            throw new IllegalArgumentException("Exit outside instance");
        if(internalLinks.containsKey(site))throw new IllegalArgumentException("Internal link cannot also be an exit");
        exits.add(site);
    }
    public void preparationStarted(String contentId) {
        require(Phase.NEW);
        if(contentId == null || contentId.isBlank()) throw new IllegalArgumentException("Missing content ID");
        this.structureId=contentId; phase=Phase.PREPARING;
    }
    public void reserve(String structureId, Destination destination) {
        if(phase!=Phase.NEW && phase!=Phase.PREPARING) throw new IllegalStateException("Already allocated or failed");
        this.structureId=Objects.requireNonNull(structureId);
        this.destination=Objects.requireNonNull(destination); phase=Phase.RESERVED;
    }
    public void generationStarted() { require(Phase.RESERVED); phase=Phase.GENERATING; }
    /** Allocation is durable before native rifts exist. Only its arrival can be resolved afterwards. */
    public void resolveReservedArrival(Destination resolved) {
        require(Phase.RESERVED);
        if(!destination.dimension().equals(resolved.dimension()) || destination.nativePocketId()!=resolved.nativePocketId()
                || !destination.bounds().equals(resolved.bounds()))
            throw new IllegalArgumentException("Native generation changed its journaled reservation");
        destination=resolved;
    }
    /** WDA interior geometry is available only after placement. Keep the original allocation and all travel rules. */
    public void resolveWdaStartRoomArrival(Site arrival) {
        require(Phase.GENERATING);
        if(selection==null || selection.content().provider()!=DungeonSpec.Provider.WDA
                || !selection.content().environment().entryMode().interior() || !exits.isEmpty())
            throw new IllegalStateException("Only unfinished start-room WDA content can resolve its arrival");
        destination=new Destination(destination.dimension(),destination.nativePocketId(),destination.bounds(),arrival);
    }
    public void generationComplete() { require(Phase.GENERATING); phase=Phase.READY; }
    public void generationFailed(String reason) {
        if (phase!=Phase.NEW && phase!=Phase.PREPARING && phase!=Phase.RESERVED && phase!=Phase.GENERATING)
            throw new IllegalStateException("Cannot fail generation from " + phase);
        phase=Phase.FAILED; failure=Objects.requireNonNull(reason);
    }
    public void recoverInterruptedGeneration() {
        // An interruption cannot prove whether allocation or world writes happened: quarantine conservatively.
        if (phase==Phase.PREPARING || phase==Phase.GENERATING || phase==Phase.RESERVED)
            generationFailed("Generation interrupted; reservation retained. Automatic regeneration is disabled.");
    }
    public void quarantine(String reason) {
        if(phase==Phase.CLOSED)throw new IllegalStateException("Cannot quarantine a closed entrance");
        phase=Phase.FAILED;failure=Objects.requireNonNull(reason);
    }
    public void entered(UUID player) { require(Phase.READY); visits.entered(player); }
    public boolean beginClosingIfEmpty() {
        if ((phase==Phase.READY || (phase==Phase.NEW && kind==PortalKind.UNSTABLE)) && kind.closesAfterVisit()
                && livingVisitors.isEmpty() && visits.allSafelyExited()) { phase=Phase.CLOSING; return true; }
        return false;
    }
    public void entranceRemoved() { require(Phase.CLOSING); phase=Phase.CLOSED; }
    public boolean recordBossDefeat(String encounterId) {
        if (encounterId==null || encounterId.isBlank()) throw new IllegalArgumentException("Empty encounter ID");
        return defeatedBosses.add(encounterId);
    }
    private void require(Phase expected) {
        if (phase!=expected) throw new IllegalStateException("Expected " + expected + ", got " + phase);
    }
}
