package dev.expedition.bridge.domain;

import java.util.Objects;
import java.util.UUID;

/** Persist before any generation or teleport. A retry may reselect unsafe footing, but never the outcome. */
public final class TravelOperation {
    public enum Outcome { DESTINATION, LIMBO }
    public enum State { PREPARED, LANDED }
    private final UUID id,player,portal;
    private final TravelRoute route;
    private final PortalRecord.Site source,anchor;
    private final Outcome outcome;
    private State state;
    private PortalRecord.Site landing;
    private long releaseAfter;
    public TravelOperation(UUID id,UUID player,UUID portal,TravelRoute route,PortalRecord.Site source,
                           PortalRecord.Site anchor,Outcome outcome,State state,PortalRecord.Site landing,long releaseAfter) {
        this.id=Objects.requireNonNull(id);this.player=Objects.requireNonNull(player);this.portal=Objects.requireNonNull(portal);
        this.route=Objects.requireNonNull(route);this.source=Objects.requireNonNull(source);this.anchor=Objects.requireNonNull(anchor);
        this.outcome=Objects.requireNonNull(outcome);this.state=Objects.requireNonNull(state);this.landing=landing;this.releaseAfter=releaseAfter;
        if(!anchor.dimension().equals("minecraft:overworld"))throw new IllegalArgumentException("Exploration anchor must be overworld");
        if(outcome==Outcome.LIMBO && route.limboBasisPoints()==0)throw new IllegalArgumentException("Risk-free route cannot select Limbo");
        if(state==State.LANDED && landing==null)throw new IllegalArgumentException("Completed transit missing destination");
        if(landing!=null)validateLanding(landing);
    }
    public UUID id(){return id;} public UUID player(){return player;} public UUID portal(){return portal;}
    public TravelRoute route(){return route;} public PortalRecord.Site source(){return source;}
    public PortalRecord.Site anchor(){return anchor;} public Outcome outcome(){return outcome;}
    public State state(){return state;} public PortalRecord.Site landing(){return landing;} public long releaseAfter(){return releaseAfter;}
    public boolean matches(UUID portal,TravelRoute route,PortalRecord.Site source) {
        return this.portal.equals(portal) && this.route==route && this.source.equals(source);
    }
    public void bindLanding(PortalRecord.Site landing) {
        if(state!=State.PREPARED)throw new IllegalStateException("Completed transit cannot move again");
        validateLanding(Objects.requireNonNull(landing));this.landing=landing;
    }
    private void validateLanding(PortalRecord.Site landing) {
        if((outcome==Outcome.LIMBO)!=landing.dimension().equals("dimdoors:limbo"))
            throw new IllegalArgumentException("Landing contradicts persisted outcome");
        if(outcome==Outcome.DESTINATION && !route.living()) {
            String expected=route.entering()?"dimdoors:dungeon_pockets":"minecraft:overworld";
            if(!landing.dimension().equals(expected))throw new IllegalArgumentException("Landing violates route direction");
        }
    }
    public void landed(long now) {
        if(landing==null)throw new IllegalStateException("Missing landing intent");
        if(state==State.LANDED)return;
        state=State.LANDED;releaseAfter=Math.addExact(now,20);
    }
    public boolean mayRelease(PortalRecord.Site observed,long now) {
        return state==State.LANDED && now>=releaseAfter && !near(observed,source) && !near(observed,landing);
    }
    private static boolean near(PortalRecord.Site a,PortalRecord.Site b) {
        return a.dimension().equals(b.dimension()) && Math.abs((long)a.x()-b.x())<=2
                && Math.abs((long)a.y()-b.y())<=3 && Math.abs((long)a.z()-b.z())<=2;
    }
}
