package dev.expedition.bridge.domain;

public enum PortalKind {
    EXPLORATION(false), GOLD(true), UNSTABLE(true);
    private final boolean closesAfterVisit;
    PortalKind(boolean closesAfterVisit) { this.closesAfterVisit = closesAfterVisit; }
    public boolean closesAfterVisit() { return closesAfterVisit; }
    public void validateTier(int tier) {
        if (this == EXPLORATION ? tier < 1 || tier > 5 : tier != 0)
            throw new IllegalArgumentException("Invalid tier for " + this + ": " + tier);
    }
}
