package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.server.level.ServerLevel;
import java.util.function.BooleanSupplier;

/** A relocated native campsite supplies its own landmarks and post-placement survey. */
interface CampLayout {
    CampLayout move(int x, int y, int z);
    BooleanSupplier survey(ServerLevel world, IslandPlan island, int exits);
}
