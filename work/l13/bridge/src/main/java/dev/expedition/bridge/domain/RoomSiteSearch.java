package dev.expedition.bridge.domain;

import java.util.Optional;

/** Incremental inspection of already-placed original geometry; never modifies blocks. */
public interface RoomSiteSearch {
    void step(int budget);
    boolean complete();
    Optional<BoardingRouteSearch.Cell> result();
}
