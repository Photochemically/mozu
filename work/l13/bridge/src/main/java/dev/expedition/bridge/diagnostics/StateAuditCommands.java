package dev.expedition.bridge.diagnostics;

import com.google.gson.*;
import dev.expedition.bridge.ExpeditionBridge;
import dev.expedition.bridge.content.DungeonCatalogLoader;
import dev.expedition.bridge.domain.PortalRecord;
import dev.expedition.bridge.storage.ExpeditionSavedData;
import dev.expedition.bridge.worldgen.NaturalEntryConfig;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class StateAuditCommands {
    @SubscribeEvent public void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("expedition").requires(source->source.hasPermission(2))
                .then(Commands.literal("audit").then(Commands.literal("snapshot").executes(c->capture(c.getSource(),null))
                        .then(Commands.argument("portal",UuidArgument.uuid()).executes(c->capture(c.getSource(),UuidArgument.getUuid(c,"portal")))))));
    }
    private int capture(CommandSourceStack source,UUID id) {
        var server=source.getServer();
        try {
            var data=ExpeditionSavedData.get(server);
            if(id!=null && data.get(id)==null)throw new IllegalArgumentException("没有这个探索实例："+id);
            var portals=id==null?List.copyOf(data.records()):List.of(data.get(id));
            if(portals.size()>2000)throw new IllegalArgumentException("实例超过2000，请指定实例UUID导出，避免过大报告");
            var operations=data.travel().operations().stream().filter(o->id==null||o.portal().equals(id)).toList();
            if(operations.size()>10000)throw new IllegalArgumentException("传送记录超过10000，请指定实例UUID导出");
            var report=StateAuditSnapshot.capture(portals,operations);
            report.addProperty("capturedAtUtc",java.time.Instant.now().toString());report.addProperty("gameTime",server.overworld().getGameTime());
            report.addProperty("saveSchema",ExpeditionSavedData.SCHEMA);report.addProperty("scope",id==null?"all_instances":id.toString());
            report.addProperty("catalogEnabled",DungeonCatalogLoader.current().enabled());
            report.addProperty("naturalEntrancesEnabled",NaturalEntryConfig.ENABLED.get());
            var versions=new JsonObject();for(String mod:List.of("minecraft","forge","dimdoors","dungeons_arise",ExpeditionBridge.MOD_ID))
                versions.addProperty(mod,ModList.get().getModContainerById(mod).map(m->m.getModInfo().getVersion().toString()).orElse("unavailable"));
            report.add("versions",versions);
            var dimensions=new JsonArray();for(var level:server.getAllLevels()) {
                var d=new JsonObject();d.addProperty("id",level.dimension().location().toString());d.addProperty("type",level.dimensionTypeId().location().toString());
                d.addProperty("minY",level.getMinBuildHeight());d.addProperty("height",level.getHeight());
                d.addProperty("loadedChunkCount",level.getChunkSource().getLoadedChunksCount());
                d.addProperty("vanillaForcedChunkCount",level.getForcedChunks().size());dimensions.add(d);
            }report.add("dimensions",dimensions);
            report.getAsJsonArray("limitations").add("Forced chunk count excludes arbitrary mod tickets; no absence-of-loading claim");
            var players=new JsonArray();for(var player:server.getPlayerList().getPlayers()) {
                var p=new JsonObject();p.addProperty("id",player.getUUID().toString());p.addProperty("name",player.getGameProfile().getName());
                p.addProperty("alive",player.isAlive());var pos=player.blockPosition();
                p.add("position",StateAuditSnapshot.site(new PortalRecord.Site(player.level().dimension().location().toString(),pos.getX(),pos.getY(),pos.getZ())));players.add(p);
            }report.add("onlinePlayers",players);
            var dir=server.getWorldPath(LevelResource.ROOT).resolve("expedition_reports");Files.createDirectories(dir);
            var target=dir.resolve("state-"+UUID.randomUUID()+".json");var temp=Files.createTempFile(dir,".state-",".tmp");
            try {
                Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(report),StandardCharsets.UTF_8);
                try {Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE);}
                catch(AtomicMoveNotSupportedException ignored){Files.move(temp,target);}
            } finally {Files.deleteIfExists(temp);}
            source.sendSuccess(()->Component.literal("已导出 "+portals.size()+" 个实例的当前记录："+target+"。这是诊断快照，不是验收通过报告。"),false);
            return 1;
        } catch(Exception error) {
            ExpeditionBridge.LOGGER.error("Cannot export state audit",error);source.sendFailure(Component.literal("导出失败："+error.getMessage()));return 0;
        }
    }
}
