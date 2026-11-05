package dev.expedition.bridge;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import dev.expedition.bridge.content.PocketIdentity;
import dev.expedition.bridge.portal.ManagedEntranceTarget;
import dev.expedition.bridge.portal.PortalVisitEvents;
import dev.expedition.bridge.portal.ManagedDoorProtection;
import net.minecraftforge.common.MinecraftForge;
import dev.expedition.bridge.content.wda.WdaBuildQueue;
import dev.expedition.bridge.portal.ManagedExitTarget;
import dev.expedition.bridge.portal.TravelEvents;
import dev.expedition.bridge.portal.FirstVisitService;
import dev.expedition.bridge.content.DungeonCatalogLoader;
import dev.expedition.bridge.worldgen.NaturalEntryConfig;
import dev.expedition.bridge.worldgen.NaturalEntryEvents;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import dev.expedition.bridge.diagnostics.PlanningAuditCommands;
import dev.expedition.bridge.diagnostics.StateAuditCommands;
import dev.expedition.bridge.diagnostics.AcceptanceCommands;
import dev.expedition.bridge.compat.WdaAliasPacks;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ExpeditionBridge.MOD_ID)
public final class ExpeditionBridge {
    public static final String MOD_ID = "expedition_bridge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ExpeditionBridge() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(WdaAliasPacks::register);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON,NaturalEntryConfig.SPEC,"expedition-bridge-common.toml");
        PocketIdentity.registerType();
        ManagedEntranceTarget.registerType();
        ManagedExitTarget.registerType();
        MinecraftForge.EVENT_BUS.register(new TravelEvents());
        MinecraftForge.EVENT_BUS.register(new PortalVisitEvents());
        MinecraftForge.EVENT_BUS.register(new DungeonCatalogLoader.Events());
        MinecraftForge.EVENT_BUS.register(FirstVisitService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new NaturalEntryEvents());
        MinecraftForge.EVENT_BUS.register(new PlanningAuditCommands());
        MinecraftForge.EVENT_BUS.register(new StateAuditCommands());
        MinecraftForge.EVENT_BUS.register(new AcceptanceCommands());
        MinecraftForge.EVENT_BUS.register(new ManagedDoorProtection());
        MinecraftForge.EVENT_BUS.register(WdaBuildQueue.INSTANCE);
        LOGGER.info("Expedition bridge development build; gameplay activation requires a reviewed dungeon catalog.");
    }
}
