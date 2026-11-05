package dev.expedition.bridge.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Only confirmed departure releases a participant. Logging out and death never do. */
public final class VisitLedger {
    public enum Presence { ENTERING, ACTIVE, DISCONNECTED, AWAITING_RESPAWN, EXITED }
    private final Map<UUID, Presence> participants = new LinkedHashMap<>();
    private boolean hasArrived;

    public VisitLedger() {}
    public VisitLedger(Map<UUID, Presence> saved) {
        this(saved,saved.values().stream().anyMatch(p->p!=Presence.ENTERING));
    }
    public VisitLedger(Map<UUID, Presence> saved, boolean hasArrived) {
        saved.forEach((id,state)->participants.put(Objects.requireNonNull(id),Objects.requireNonNull(state)));
        this.hasArrived=hasArrived;
    }
    public Map<UUID, Presence> snapshot() { return Map.copyOf(participants); }
    public boolean hasParticipant(UUID player) { return participants.containsKey(player); }
    public boolean enteredBefore() { return hasArrived; }
    public void entryIntent(UUID player) { participants.put(Objects.requireNonNull(player),Presence.ENTERING); }
    public void entered(UUID player) { participants.put(Objects.requireNonNull(player),Presence.ACTIVE);hasArrived=true; }
    public void disconnected(UUID player) {
        if (participants.get(player) == Presence.ACTIVE || participants.get(player)==Presence.ENTERING) participants.put(player,Presence.DISCONNECTED);
    }
    public void died(UUID player) {
        if (participants.containsKey(player) && participants.get(player) != Presence.EXITED)
            participants.put(player,Presence.AWAITING_RESPAWN);
    }
    /** Caller must establish that this living player is outside the tracked destination. */
    public void confirmedSafeExit(UUID player) {
        if (!participants.containsKey(player)) throw new IllegalArgumentException("Player did not enter this portal");
        participants.put(player,Presence.EXITED);
    }
    public boolean allSafelyExited() {
        return enteredBefore() && participants.values().stream().allMatch(p -> p == Presence.EXITED);
    }
}
