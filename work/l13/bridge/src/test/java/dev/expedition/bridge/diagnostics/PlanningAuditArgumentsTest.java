package dev.expedition.bridge.diagnostics;

import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanningAuditArgumentsTest {
    @Test void acceptsUnquotedNamespacedIdsAndAllWithoutEatingFollowingParameters() throws Exception {
        var reader=new StringReader("dungeons_arise:lighthouse 12345 96 8 8 32 grass");
        assertEquals("dungeons_arise:lighthouse",ResourceLocationArgument.id().parse(reader).toString());
        assertEquals(" 12345 96 8 8 32 grass",reader.getRemaining());
        assertEquals("minecraft:all",ResourceLocationArgument.id().parse(new StringReader("all 12345")).toString());
    }
}
