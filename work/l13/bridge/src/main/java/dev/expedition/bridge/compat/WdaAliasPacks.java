package dev.expedition.bridge.compat;

import dev.expedition.bridge.ExpeditionBridge;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.*;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;
import java.io.*;
import java.nio.file.Files;

public final class WdaAliasPacks {
    private WdaAliasPacks(){}
    public static void register(AddPackFindersEvent event) {
        if(event.getPackType()!=PackType.SERVER_DATA)return;
        var mod=ModList.get().getModFileById("dungeons_arise");
        if(mod==null)throw new IllegalStateException("Required WDA mod unavailable");
        var resources=WdaAliasData.build(relative->{
            var path=mod.getFile().findResource("data","dungeons_arise",relative);
            try {return Files.isRegularFile(path)?Files.readAllBytes(path):null;}
            catch(IOException error){throw new UncheckedIOException("Cannot read installed WDA alias source "+relative,error);}
        });
        if(resources.isEmpty())return;
        event.addRepositorySource(consumer->{
            var pack=Pack.readMetaAndCreate("expedition_wda_resource_aliases",Component.literal("Expedition WDA resource aliases"),true,
                    id->new WdaAliasPack(id,resources),PackType.SERVER_DATA,Pack.Position.BOTTOM,PackSource.BUILT_IN);
            if(pack==null)throw new IllegalStateException("Cannot create WDA alias pack");
            consumer.accept(pack);
        });
        ExpeditionBridge.LOGGER.info("Offering {} WDA compatibility resources; unresolved resources remain blocked",resources.size());
        for(var substitute:WdaAliasData.SUBSTITUTIONS)if(resources.containsKey(substitute.missing()))
            ExpeditionBridge.LOGGER.info("WDA approved compatibility substitute: {} -> {} (not a recovered original)",
                    substitute.missing(),substitute.source());
    }
}
