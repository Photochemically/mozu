package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Searches existing rooms after native placement. One door per selected room, starting piece first. No block writes. */
final class WdaInteriorSitesJob {
    private final List<InteriorRoom> remaining;
    private final List<Cell> selected=new ArrayList<>();
    private final InteriorBlockProbe probe;
    private final String dimension;
    private final int count;
    private RoomSiteSearch search;
    private final boolean aerial;
    WdaInteriorSitesJob(ServerLevel world,List<InteriorRoom> rooms,int count,boolean aerial) {
        if(rooms.isEmpty() || count<1 || count>4)throw new IllegalArgumentException("Missing interior rooms or invalid exit count");
        remaining=new ArrayList<>(rooms);probe=new InteriorBlockProbe(world);dimension=world.dimension().location().toString();this.count=count;this.aerial=aerial;
        search=search(remaining.remove(0));
    }
    boolean step(int budget,long nanos) {
        long started=System.nanoTime();int work=0;
        while(work<budget && (work==0 || System.nanoTime()-started<nanos)) {
            search.step(1);work++;
            if(!search.complete())continue;
            var site=search.result();
            if(site.isEmpty() && selected.isEmpty())throw new IllegalStateException("Original starting room has no safe entry; refusing alternate room or tunnel");
            site.ifPresent(selected::add);
            if(selected.size()==count)return true;
            if(remaining.isEmpty())throw new IllegalStateException("Cannot place requested exits in distinct original rooms; refusing reduced exit count");
            // Farthest-first improves spatial coverage; ties retain original native piece order.
            int next=0;long farthest=-1;
            for(int i=0;i<remaining.size();i++) {
                var b=remaining.get(i).bounds();var center=new Cell(b.minX()+(b.width()-1)/2,b.minY()+(b.height()-1)/2,b.minZ()+(b.depth()-1)/2);
                long distance=selected.stream().mapToLong(p->Math.abs((long)p.x()-center.x())+Math.abs((long)p.y()-center.y())+Math.abs((long)p.z()-center.z())).min().orElse(0);
                if(distance>farthest){next=i;farthest=distance;}
            }
            search=search(remaining.remove(next));
        }
        return false;
    }
    List<PortalRecord.Site> sites() {
        if(selected.size()!=count)throw new IllegalStateException("Interior sites incomplete");
        return selected.stream().map(p->new PortalRecord.Site(dimension,p.x(),p.y(),p.z())).toList();
    }
    private RoomSiteSearch search(InteriorRoom room){return aerial?new AerialSiteSearch(room,probe,selected):new InteriorSiteSearch(room,probe,selected);}
}
