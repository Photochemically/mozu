package dev.expedition.bridge.domain;

import java.util.*;

/** Original rigid piece and its horizontal air-ending passage joints; not arbitrary cavity coordinates. */
public record InteriorRoom(BlockBounds bounds,List<BoardingRouteSearch.Cell> passages) {
    public InteriorRoom {Objects.requireNonNull(bounds);passages=List.copyOf(passages);}
    public InteriorRoom move(int x,int y,int z) {
        return new InteriorRoom(bounds.move(x,y,z),passages.stream().map(p->p.move(x,y,z)).toList());
    }
    public static void requireRockCover(IslandPlan island) {
        if(island.surface()!=IslandPlan.Surface.ROCK || (long)island.firstFreeY()-1-island.building().maxY()<4)
            throw new IllegalArgumentException("Underground entry requires rock terrain and at least four blocks above the complete building");
    }
}
