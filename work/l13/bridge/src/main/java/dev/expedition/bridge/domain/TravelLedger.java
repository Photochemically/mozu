package dev.expedition.bridge.domain;

import java.util.*;
import java.util.function.IntSupplier;

public final class TravelLedger {
    private final Map<UUID,TravelOperation> active=new LinkedHashMap<>();
    public Collection<TravelOperation> operations(){return List.copyOf(active.values());}
    public void restore(TravelOperation op) {
        if(active.containsKey(op.id()) || active.values().stream().anyMatch(old->old.player().equals(op.player())
                && old.matches(op.portal(),op.route(),op.source())))throw new IllegalArgumentException("Duplicate transit identity or trigger");
        active.put(op.id(),op);
    }
    public TravelOperation begin(UUID player,UUID portal,TravelRoute route,PortalRecord.Site source,
                                 PortalRecord.Site anchor,IntSupplier draw) {
        var existing=active.values().stream().filter(op->op.player().equals(player) && op.portal().equals(portal) && op.route()==route
                && (op.matches(portal,route,source) || op.state()==TravelOperation.State.PREPARED)).findFirst().orElse(null);
        if(existing!=null) {
            return existing; // Includes retries after restart and repeated callbacks after success.
        }
        if(active.values().stream().anyMatch(op->op.player().equals(player) && op.state()==TravelOperation.State.LANDED))
            throw new IllegalStateException("Move clear of the previous arrival before another managed transit");
        int sample=route.limboBasisPoints()==0?9999:draw.getAsInt();
        if(sample<0 || sample>=10000)throw new IllegalArgumentException("Invalid probability sample");
        var outcome=sample<route.limboBasisPoints()?TravelOperation.Outcome.LIMBO:TravelOperation.Outcome.DESTINATION;
        var op=new TravelOperation(UUID.randomUUID(),player,portal,route,source,anchor,outcome,TravelOperation.State.PREPARED,null,0);
        active.put(op.id(),op);return op;
    }
    public boolean releaseIfClear(UUID player,PortalRecord.Site observed,long now) {
        return active.values().removeIf(op->op.player().equals(player) && op.mayRelease(observed,now));
    }
}
