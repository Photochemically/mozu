package dev.expedition.bridge.diagnostics;

import com.google.gson.*;
import dev.expedition.bridge.content.wda.WdaStructurePlanner;
import java.util.*;

/** Explicitly separates planned geometry from unperformed in-game acceptance. */
public final class PlanAuditReport {
    private final JsonObject root=new JsonObject();
    private final JsonArray results=new JsonArray();
    private final Set<String> remaining;
    private String state="running";
    private int fitting,oversized,errors;
    public PlanAuditReport(String id,List<String> structures,long seed,WdaStructurePlanner.Environment environment) {
        if(structures.isEmpty() || new HashSet<>(structures).size()!=structures.size())throw new IllegalArgumentException("Need unique audit structures");
        remaining=new LinkedHashSet<>(structures);var gson=new Gson();
        root.addProperty("schema",1);root.addProperty("runId",id);root.addProperty("scope","native planning only; no pocket allocation, block placement or player teleport");
        root.addProperty("seed",seed);root.add("environment",gson.toJsonTree(environment));root.add("structures",gson.toJsonTree(structures));
        root.addProperty("runtimeAcceptancePassed",false);root.add("results",results);
    }
    public void record(String id,WdaStructurePlanner.Inspection inspection,String failure,long nanos) {
        if(!state.equals("running") || !remaining.contains(id) || (inspection==null)==(failure==null))throw new IllegalStateException("Invalid audit result");
        if(inspection!=null && !inspection.structure().equals(id))throw new IllegalArgumentException("Mismatched audit structure");
        var result=new JsonObject();result.addProperty("structure",id);result.addProperty("planningMilliseconds",nanos/1_000_000.0);
        if(inspection!=null) {
            result.addProperty("outcome",inspection.heightFits()?"planned_height_fits":"height_limit");result.add("inspection",new Gson().toJsonTree(inspection));
            if(inspection.heightFits())fitting++;else oversized++;
        } else {result.addProperty("outcome","error");result.addProperty("error",failure);errors++;}
        remaining.remove(id);results.add(result);
    }
    public void finish(){if(!state.equals("running") || !remaining.isEmpty())throw new IllegalStateException("Incomplete audit");state="finished";}
    public void cancel(){if(state.equals("running"))state="cancelled";}
    public JsonObject snapshot() {
        var result=root.deepCopy();result.addProperty("state",state);result.addProperty("plannedHeightFits",fitting);
        result.addProperty("heightLimits",oversized);result.addProperty("errors",errors);result.add("notChecked",new Gson().toJsonTree(remaining));return result;
    }
}
