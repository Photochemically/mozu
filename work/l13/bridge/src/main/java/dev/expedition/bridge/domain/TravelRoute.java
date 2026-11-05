package dev.expedition.bridge.domain;

/** Confirmed probabilities in basis points. Living-space traffic never inherits exploration risk. */
public enum TravelRoute {
    EXPLORATION_ENTER(300,true), EXPLORATION_EXIT(200,false),
    UNSTABLE_ENTER(500,true), UNSTABLE_EXIT(300,false),
    GOLD_ENTER(0,true), GOLD_EXIT(0,false), LIVING_ENTER(0,true), LIVING_EXIT(0,false);
    private final int limboBasisPoints;
    private final boolean entering;
    TravelRoute(int chance,boolean entering) { limboBasisPoints=chance;this.entering=entering; }
    public int limboBasisPoints() { return limboBasisPoints; }
    public boolean entering() { return entering; }
    public boolean living() { return this==LIVING_ENTER || this==LIVING_EXIT; }
    public static TravelRoute of(PortalKind kind,boolean enter) {
        return switch(kind) {
            case GOLD -> enter?GOLD_ENTER:GOLD_EXIT;
            case UNSTABLE -> enter?UNSTABLE_ENTER:UNSTABLE_EXIT;
            case EXPLORATION -> enter?EXPLORATION_ENTER:EXPLORATION_EXIT;
        };
    }
}
