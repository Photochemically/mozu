package dev.expedition.bridge.domain;

import java.util.*;
import java.util.function.Predicate;

/** Raw WDA 2.1.58 gaps; runtime resource presence includes explicit repairs and approved substitutes. */
public final class WdaKnownResourceGaps {
    public record Reference(boolean pool,String id){}
    private static final Map<String,List<Reference>> REFERENCES=Map.of(
            "dungeons_arise:bandit_village",List.of(new Reference(false,"dungeons_arise:bandit_village/bandit_village_deco_3")),
            "dungeons_arise:foundry",List.of(new Reference(true,"dungeons_arise:underworld/foundry/foundry_corridor_gears")),
            "dungeons_arise:mechanical_nest",List.of(new Reference(true,"dungeons_arise:mechanical_nest/mechanical_nest_decoration"),
                    new Reference(false,"dungeons_arise:mechanical_nest/mechanical_nest_bridge_terminator_6_"),
                    new Reference(false,"dungeons_arise:mechanical_nest/mechanical_nest_spawner_6")),
            "dungeons_arise:thornborn_towers",List.of(new Reference(false,"dungeons_arise:thornborn_towers/thornborn_towers_hanging_bridge_2_medium_terminator")));
    private WdaKnownResourceGaps(){}
    public static List<Reference> missing(String structure,Predicate<String> poolPresent,Predicate<String> templatePresent) {
        return REFERENCES.getOrDefault(structure,List.of()).stream().filter(r->!(r.pool()?poolPresent:templatePresent).test(r.id())).toList();
    }
}
