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
    private Phase phase;
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
        this.failure=Objects.requireNonNull(failure);
        if ((phase==Phase.RESERVED || phase==Phase.GENERATING || phase==Phase.READY) && destination==null)
            throw new IllegalArgumentException("Active portal is missing a destination");
    }
    public UUID id() { return id; }
    public PortalKind kind() { return kind; }
    public int tier() { return tier; }
    public Site entrance() { return entrance; }
    public long seed() { return seed; }
    public Phase phase() { return phase; }
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
