package dev.expedition.bridge.domain;

import java.util.*;

/** Original endpoints and supports only; ordinary walls and player-built devices are not enrolled. */
public final class ProtectedBlockPlan {
    private ProtectedBlockPlan() {}
    public static Set<PortalRecord.Site> routes(PortalRecord.Destination destination,NativeRiftPlan routes) {
        var endpoints=new HashSet<>(routes.exits());endpoints.addAll(routes.internalLinks().keySet());endpoints.addAll(routes.internalLinks().values());
        var result=new HashSet<PortalRecord.Site>();
        for(var endpoint:endpoints) {
            if(!NativeRiftPlan.inside(destination,endpoint))throw new IllegalArgumentException("Protected endpoint outside instance");
            for(int dy=-1;dy<=1;dy++) {
                var pos=new PortalRecord.Site(endpoint.dimension(),endpoint.x(),Math.addExact(endpoint.y(),dy),endpoint.z());
                if(NativeRiftPlan.inside(destination,pos))result.add(pos);
            }
        }
        return Set.copyOf(result);
    }
}
