package dev.expedition.bridge.diagnostics;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanningAuditArgumentsTest {
    @Test void readOnlyPlannerParsesLegacyAndDedicatedEntryForms() {
        var dispatcher=new CommandDispatcher<CommandSourceStack>();
        new PlanningAuditCommands().register(new RegisterCommandsEvent(dispatcher,Commands.CommandSelection.ALL,null));
        // Parse the actual registered subcommand without constructing an operator or starting a server.
        var parser=new CommandDispatcher<CommandSourceStack>();
        parser.getRoot().addChild(dispatcher.getRoot().getChild("expedition").getChild("audit").getChild("wda"));
        for(String args:new String[]{"water 32 shore_dive","water 12","rock start_well","grass start_mine","void start_deck"}) {
            var parsed=parser.parse("wda dungeons_arise:typhon 12345 96 8 6 24 "+args,(CommandSourceStack)null);
            assertFalse(parsed.getReader().canRead(),args);
            assertNotNull(parsed.getContext().getCommand(),args);
        }
        var unknown=parser.parse("wda dungeons_arise:typhon 12345 96 8 6 24 water 32 unknown",(CommandSourceStack)null);
        assertTrue(unknown.getReader().canRead());
    }
    @Test void acceptsUnquotedNamespacedIdsAndAllWithoutEatingFollowingParameters() throws Exception {
        var reader=new StringReader("dungeons_arise:lighthouse 12345 96 8 8 32 grass");
        assertEquals("dungeons_arise:lighthouse",ResourceLocationArgument.id().parse(reader).toString());
        assertEquals(" 12345 96 8 8 32 grass",reader.getRemaining());
        assertEquals("minecraft:all",ResourceLocationArgument.id().parse(new StringReader("all 12345")).toString());
    }
}
