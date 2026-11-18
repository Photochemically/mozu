package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.InteriorSiteSearch;
import dev.expedition.bridge.domain.IslandPlan;

/** Ground access beside all three exposed sections; the buried chest remains an excavation reward. */
final class HindRoute {
    interface Probe extends InteriorSiteSearch.Probe {boolean feature(HindLayout.Feature f);}
    private final HindLayout layout;
    private final Probe world;
    private final DryPerimeterRoute ground;
    private boolean connected;
    private int checked;
    HindRoute(HindLayout layout,IslandPlan island,int exits,Probe world){
        this.layout=layout;this.world=world;
        if(island.firstFreeY()!=layout.groundY())throw new IllegalStateException("Changed fossil burial depth");
        approaches();ground=new DryPerimeterRoute(island,exits,world,layout.approaches(),layout.approaches());
    }
    private void approaches(){for(var p:layout.approaches())if(!InteriorSiteSearch.clearPlatform(p,world))throw new IllegalStateException("Obstructed fossil approach");}
    boolean step(){
        if(!connected){if(!ground.step())return false;connected=true;}
        // Each original block check consumes a work step; do not scan the whole fossil in one server tick.
        if(checked<layout.features().size()){
            var f=layout.features().get(checked++);if(!world.feature(f))throw new IllegalStateException("Changed original fossil content at "+f.pos());return false;
        }
        approaches();return true;
    }
}
