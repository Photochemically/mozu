package dev.expedition.bridge.content;

import com.google.gson.*;
import dev.expedition.bridge.domain.*;
import java.util.*;

/** Shared by reload resources and the saved immutable selection; explicit numeric validation avoids truncation. */
public final class DungeonSpecJson {
    private DungeonSpecJson(){}
    public static DungeonSpec read(String id,JsonObject json) {
        if(integer(json,"schema")!=1)throw new IllegalArgumentException("Unsupported dungeon entry schema");
        var provider=DungeonSpec.Provider.valueOf(json.get("provider").getAsString().toUpperCase(Locale.ROOT));
        var tiers=new LinkedHashSet<Integer>();for(var value:json.getAsJsonArray("tiers")) {
            int tier=value.getAsBigDecimal().intValueExact();if(!tiers.add(tier))throw new IllegalArgumentException("Duplicate tier");
        }
        DungeonSpec.Environment environment=null;
        if(json.has("environment")) {
            var e=json.getAsJsonObject("environment");environment=new DungeonSpec.Environment(integer(e,"first_free_y"),integer(e,"padding"),
                    integer(e,"rim"),integer(e,"foundation_depth"),IslandPlan.Surface.valueOf(e.get("surface").getAsString().toUpperCase(Locale.ROOT)),
                    e.has("water_depth")?integer(e,"water_depth"):0,optionalBoolean(e,"boarding"),
                    e.has("entry_mode")?DungeonSpec.EntryMode.valueOf(e.get("entry_mode").getAsString().toUpperCase(Locale.ROOT)):DungeonSpec.EntryMode.PERIMETER);
        }
        return new DungeonSpec(id,provider,json.get("target").getAsString(),tiers,integer(json,"weight"),environment,integer(json,"exits"));
    }
    public static JsonObject write(DungeonSpec spec) {
        var j=new JsonObject();j.addProperty("schema",1);j.addProperty("provider",spec.provider().name().toLowerCase(Locale.ROOT));
        j.addProperty("target",spec.target());var tiers=new JsonArray();spec.tiers().stream().sorted().forEach(tiers::add);j.add("tiers",tiers);
        j.addProperty("weight",spec.weight());j.addProperty("exits",spec.exits());
        if(spec.environment()!=null) {
            var e=spec.environment();var env=new JsonObject();env.addProperty("first_free_y",e.firstFreeY());env.addProperty("padding",e.padding());
            env.addProperty("rim",e.rim());env.addProperty("foundation_depth",e.foundationDepth());env.addProperty("surface",e.surface().name().toLowerCase(Locale.ROOT));j.add("environment",env);
            if(e.waterDepth()>0)env.addProperty("water_depth",e.waterDepth());
            if(e.boarding())env.addProperty("boarding",true);
            if(e.entryMode()!=DungeonSpec.EntryMode.PERIMETER)env.addProperty("entry_mode",e.entryMode().name().toLowerCase(Locale.ROOT));
        }
        return j;
    }
    public static int integer(JsonObject json,String key){return json.get(key).getAsBigDecimal().intValueExact();}
    private static boolean optionalBoolean(JsonObject json,String key) {
        if(!json.has(key))return false;
        if(!(json.get(key) instanceof JsonPrimitive value) || !value.isBoolean())throw new IllegalArgumentException("Expected boolean: "+key);
        return value.getAsBoolean();
    }
}
