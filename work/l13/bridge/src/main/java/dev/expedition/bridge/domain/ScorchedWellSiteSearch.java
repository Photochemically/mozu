package dev.expedition.bridge.domain;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import java.util.*;

/** Entry stays outside the original pit. Checks its open shaft and lava without making descent safe. */
public final class ScorchedWellSiteSearch implements RoomSiteSearch {
    public interface Probe extends InteriorSiteSearch.Probe {boolean lava(Cell p);}
    private final ScorchedWellLayout layout;
    private final Probe world;
    private int lavaIndex,shaftY;
    private boolean complete;
    private Cell result;
    public ScorchedWellSiteSearch(ScorchedWellLayout layout,Probe world){this.layout=layout;this.world=world;shaftY=layout.shaftBottom().y();}
    public boolean complete(){return complete;}
    public Optional<Cell> result(){return Optional.ofNullable(result);}
    public void step(int budget) {
        if(budget<=0)throw new IllegalArgumentException("Invalid well inspection budget");
        while(!complete && budget-->0) {
            if(lavaIndex<layout.lava().size()){if(!world.lava(layout.lava().get(lavaIndex++))){complete=true;return;}continue;}
            if(shaftY<=layout.mouth().y()+1) {
                var p=new Cell(layout.mouth().x(),shaftY++,layout.mouth().z());
                if(!air(p)){complete=true;return;}continue;
            }
            complete=true;
            if(!InteriorSiteSearch.clearPlatform(layout.arrival(),world))return;
            var path=layout.approach();
            for(int i=0;i<path.size();i++) {
                var p=path.get(i);if(world.at(p.move(0,-1,0))!=Type.SUPPORT || !air(p) || !air(p.move(0,1,0)))return;
                if(i>0 && p.y()!=path.get(i-1).y() && !air(path.get(i-1).move(0,2,0)))return;
            }
            result=layout.arrival();
        }
    }
    private boolean air(Cell p){return world.at(p)==Type.AIR;}
}
