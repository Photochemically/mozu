package dev.expedition.bridge.domain;

import java.util.*;

/** A local room mechanism is not an exit to another exploration instance. */
public record NativeRiftPlan(Set<PortalRecord.Site> exits,Map<PortalRecord.Site,PortalRecord.Site> internalLinks) {
    public NativeRiftPlan { exits=Set.copyOf(exits);internalLinks=Map.copyOf(internalLinks); }
    public static NativeRiftPlan create(PortalRecord.Destination destination,Set<PortalRecord.Site> rifts,
                                       Map<PortalRecord.Site,PortalRecord.Site> localTargets,Set<PortalRecord.Site> inactive,boolean allReturn) {
        if(!rifts.contains(destination.arrival()))throw new IllegalArgumentException("Native arrival is not an original rift");
        if(!rifts.containsAll(localTargets.keySet()) || !rifts.containsAll(inactive))throw new IllegalArgumentException("Unknown native rift source");
        var exits=new LinkedHashSet<PortalRecord.Site>();var links=new LinkedHashMap<PortalRecord.Site,PortalRecord.Site>();
        for(var source:rifts) {
            if(!inside(destination,source))throw new IllegalArgumentException("Native rift outside reserved room");
            var target=localTargets.get(source);
            if(allReturn || source.equals(destination.arrival()))exits.add(source);
            else if(target!=null && inside(destination,target)) {
                if(!rifts.contains(target) || target.equals(source))throw new IllegalArgumentException("Invalid internal native link");
                links.put(source,target);
            } else if(!inactive.contains(source))exits.add(source);
        }
        return new NativeRiftPlan(exits,links);
    }
    public static boolean inside(PortalRecord.Destination destination,PortalRecord.Site site) {
        return destination.dimension().equals(site.dimension()) && destination.bounds().contains(site.x(),site.y(),site.z());
    }
}
