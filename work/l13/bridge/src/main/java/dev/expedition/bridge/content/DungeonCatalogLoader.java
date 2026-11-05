package dev.expedition.bridge.content;

import com.google.gson.*;
import dev.expedition.bridge.ExpeditionBridge;
import dev.expedition.bridge.domain.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

public final class DungeonCatalogLoader extends SimpleJsonResourceReloadListener {
    private static volatile DungeonCatalog current=DungeonCatalog.disabled();
    public DungeonCatalogLoader(){super(new Gson(),"expedition_dungeons");}
    public static DungeonCatalog current(){return current;}
    public static final class Events {
        @SubscribeEvent public void reload(AddReloadListenerEvent event){event.addListener(new DungeonCatalogLoader());}
        @SubscribeEvent public void stopped(ServerStoppedEvent event){current=DungeonCatalog.disabled();}
    }
    @Override protected void apply(Map<ResourceLocation,JsonElement> resources,ResourceManager manager,ProfilerFiller profiler) {
        var settings=resources.get(ResourceLocation.parse("expedition_bridge:settings"));
        if(settings==null)throw new IllegalArgumentException("Missing expedition catalog settings");
        var root=settings.getAsJsonObject();
        if(DungeonSpecJson.integer(root,"schema")!=1)throw new IllegalArgumentException("Unsupported catalog schema");
        boolean enabled=root.get("enabled").getAsBoolean();
        var weights=new ArrayList<Integer>();
        if(root.has("unstable_tier_weights"))for(var v:root.getAsJsonArray("unstable_tier_weights"))weights.add(v.getAsBigDecimal().intValueExact());
        var entries=new ArrayList<DungeonSpec>();
        resources.forEach((id,value)->{if(!id.toString().equals("expedition_bridge:settings"))entries.add(DungeonSpecJson.read(id.toString(),value.getAsJsonObject()));});
        var candidate=new DungeonCatalog(enabled,entries,weights); // Validate completely before replacing the previous snapshot.
        current=candidate;
        ExpeditionBridge.LOGGER.info("Exploration catalog: enabled={}, entries={}; existing instance selections retained",enabled,entries.size());
    }
}
