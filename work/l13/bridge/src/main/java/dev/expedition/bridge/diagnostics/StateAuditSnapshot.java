package dev.expedition.bridge.diagnostics;

import com.google.gson.*;
import dev.expedition.bridge.domain.*;
import java.util.*;

/** Explicit immutable JSON observation, never a repair command or a statement that gameplay passed. */
public final class StateAuditSnapshot {
    private StateAuditSnapshot(){}
    public static JsonObject capture(Collection<PortalRecord> portals,Collection<TravelOperation> operations) {
        var root=new JsonObject();root.addProperty("reportSchema",1);root.addProperty("runtimeAcceptancePassed",false);
        root.addProperty("source","current_server_memory");
        var limits=new JsonArray();
        for(var value:List.of("No chest inventory or reward refresh inspection","Boss IDs are recorded state, not proof of final-death handling",
                "No live block, rift target or destination safety inspection","No chunk loading or teleport performed"))limits.add(value);
        root.add("limitations",limits);
        var rows=new JsonArray();var ids=new HashSet<UUID>();
        portals.stream().sorted(Comparator.comparing(p->p.id().toString())).forEach(p->{
            ids.add(p.id());var row=new JsonObject();row.addProperty("id",p.id().toString());row.addProperty("kind",p.kind().name());
            row.addProperty("phase",p.phase().name());row.addProperty("configuredTier",p.tier());row.addProperty("effectiveTier",p.effectiveTier());
            row.addProperty("seed",p.seed());row.addProperty("structure",p.structureId());row.addProperty("failure",p.failure());
            row.add("entrance",site(p.entrance()));
            if(p.destination()!=null) {
                var d=p.destination();var destination=new JsonObject();destination.addProperty("dimension",d.dimension());
                destination.addProperty("nativePocketId",d.nativePocketId());destination.add("arrival",site(d.arrival()));
                var b=d.bounds();var bounds=new JsonArray();for(int v:new int[]{b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()})bounds.add(v);
                destination.add("bounds",bounds);row.add("destination",destination);
            }
            if(p.selection()!=null) {
                var selected=new JsonObject();selected.addProperty("id",p.selection().content().id());
                selected.addProperty("provider",p.selection().content().provider().name());selected.addProperty("target",p.selection().content().target());
                row.add("selection",selected);
            }
            var participants=new JsonArray();p.visits().snapshot().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{
                var participant=new JsonObject();participant.addProperty("player",e.getKey().toString());participant.addProperty("presence",e.getValue().name());
                participant.addProperty("temporarilyInLivingSpace",p.livingVisitors().contains(e.getKey()));
                var anchor=p.returnAnchor(e.getKey());if(anchor!=null)participant.add("overworldAnchor",site(anchor));participants.add(participant);
            });row.add("participants",participants);
            // Include anchors even when participation has not yet been registered (e.g. an interrupted entry).
            var anchors=new JsonObject();p.returnAnchors().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->anchors.add(e.getKey().toString(),site(e.getValue())));
            row.add("returnAnchors",anchors);
            var visitors=new JsonArray();p.livingVisitors().stream().sorted().forEach(id->visitors.add(id.toString()));row.add("livingVisitors",visitors);
            var exits=new JsonArray();p.exits().stream().sorted(Comparator.comparing(PortalRecord.Site::key)).forEach(s->exits.add(site(s)));row.add("exits",exits);
            var bosses=new JsonArray();p.defeatedBosses().stream().sorted().forEach(bosses::add);row.add("recordedDefeatedBossIds",bosses);
            row.addProperty("hasConfirmedArrival",p.visits().enteredBefore());row.addProperty("allParticipantsSafelyExited",p.visits().allSafelyExited());
            var links=new JsonArray();p.internalLinks().entrySet().stream().sorted(Comparator.comparing(e->e.getKey().key())).forEach(e->{
                var link=new JsonObject();link.add("source",site(e.getKey()));link.add("target",site(e.getValue()));links.add(link);
            });row.add("internalLinks",links);
            row.add("closureBlockers",closureBlockers(p));rows.add(row);
        });root.add("portals",rows);root.addProperty("portalCount",rows.size());
        var transits=new JsonArray();operations.stream().sorted(Comparator.comparing(o->o.id().toString())).forEach(o->{
            var row=new JsonObject();row.addProperty("id",o.id().toString());row.addProperty("player",o.player().toString());
            row.addProperty("portal",o.portal().toString());row.addProperty("portalPresentInSnapshot",ids.contains(o.portal()));
            row.addProperty("route",o.route().name());row.addProperty("outcome",o.outcome().name());row.addProperty("state",o.state().name());
            row.addProperty("releaseAfterTick",o.releaseAfter());row.add("source",site(o.source()));row.add("anchor",site(o.anchor()));
            if(o.landing()!=null)row.add("landing",site(o.landing()));transits.add(row);
        });root.add("travelOperations",transits);return root;
    }
    private static JsonArray closureBlockers(PortalRecord p) {
        var reasons=new JsonArray();
        if(!p.kind().closesAfterVisit()){reasons.add("permanent_exploration_entrance");return reasons;}
        if(p.phase()!=PortalRecord.Phase.READY && !(p.phase()==PortalRecord.Phase.NEW && p.kind()==PortalKind.UNSTABLE))reasons.add("phase_not_eligible_for_begin_close");
        if(!p.visits().enteredBefore())reasons.add("no_confirmed_arrival");
        if(!p.visits().allSafelyExited())reasons.add("participants_not_all_safely_exited");
        if(!p.livingVisitors().isEmpty())reasons.add("temporary_living_space_visit");
        return reasons;
    }
    public static JsonObject site(PortalRecord.Site s) {
        var json=new JsonObject();json.addProperty("dimension",s.dimension());json.addProperty("x",s.x());json.addProperty("y",s.y());json.addProperty("z",s.z());return json;
    }
}
