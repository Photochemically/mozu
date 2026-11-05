package dev.expedition.bridge.domain;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import java.util.Optional;

/** Read-only, budgeted post-placement check. A door is never placed in a ladder cell or on the hull. */
public final class BlimpLadderSiteSearch implements RoomSiteSearch {
    public interface Probe extends InteriorSiteSearch.Probe {boolean ladder(Cell p,int dx,int dz);}
    private final BlimpLadderLayout layout;
    private final Probe world;
    private int y;
    private boolean complete;
    private Cell result;
    public BlimpLadderSiteSearch(BlimpLadderLayout layout,Probe world){this.layout=layout;this.world=world;y=layout.foot().y();}
    public boolean complete(){return complete;}
    public Optional<Cell> result(){return Optional.ofNullable(result);}
    public void step(int budget) {
        if(budget<=0)throw new IllegalArgumentException("Invalid ladder inspection budget");
        while(!complete && budget-->0) {
            if(y<=layout.top().y()) {
                var p=new Cell(layout.foot().x(),y,layout.foot().z());
                if(!world.ladder(p,layout.dx(),layout.dz()) || !air(p.move(layout.dx(),0,layout.dz()))
                        || !air(p.move(layout.dx(),1,layout.dz()))) {complete=true;return;}
                y++;
            } else {
                complete=true;
                if(!air(layout.top().move(0,1,0)) || !air(layout.top().move(0,2,0))
                        || !standable(layout.landing()) || world.at(layout.foot().move(0,-1,0))!=Type.SUPPORT)return;
                for(int step=1;step<=2;step++)if(!standable(layout.foot().move(step*layout.dx(),0,step*layout.dz())))return;
                if(InteriorSiteSearch.clearPlatform(layout.arrival(),world))result=layout.arrival();
            }
        }
    }
    private boolean air(Cell p){return world.at(p)==Type.AIR;}
    private boolean standable(Cell p){return world.at(p.move(0,-1,0))==Type.SUPPORT && air(p) && air(p.move(0,1,0));}
}
