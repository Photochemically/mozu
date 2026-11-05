package dev.expedition.bridge.domain;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import java.util.*;

/** Read-only walk search from the audited original inner platform to a clear exterior ground door. No digging. */
public final class MushroomMineSiteSearch implements RoomSiteSearch {
    public static final int MAX_VISITED=65536;
    private final MushroomMineLayout layout;
    private final InteriorSiteSearch.Probe world;
    private final BlockBounds scan;
    private final ArrayDeque<Cell> todo=new ArrayDeque<>();
    private final Map<Cell,Cell> towardPlatform=new HashMap<>();
    private boolean complete;
    private Cell result;
    public MushroomMineSiteSearch(MushroomMineLayout layout,InteriorSiteSearch.Probe world) {
        this.layout=layout;this.world=world;scan=layout.body().expand(4);
        if(!InteriorSiteSearch.clearPlatform(layout.platform(),this::at))complete=true;
        else {todo.add(layout.platform());towardPlatform.put(layout.platform(),layout.platform());}
    }
    public boolean complete(){return complete;}
    public Optional<Cell> result(){return Optional.ofNullable(result);}
    public List<Cell> route() {
        if(result==null)return List.of();var route=new ArrayList<Cell>();
        for(var p=result;;p=towardPlatform.get(p)){route.add(p);if(p.equals(layout.platform()))return List.copyOf(route);}
    }
    public void step(int budget) {
        if(budget<=0)throw new IllegalArgumentException("Invalid mine search budget");
        while(!complete && budget-->0) {
            var p=todo.remove();
            if(exteriorDoor(p) && InteriorSiteSearch.clearPlatform(p,this::at)){result=p;complete=true;return;}
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int dy:new int[]{0,-1,1}) {
                var q=p.move(d[0],dy,d[1]);
                if(towardPlatform.containsKey(q) || q.y()<layout.groundY() || q.y()>layout.body().maxY()-2 || !standable(q))continue;
                if(dy!=0 && !passable(at((p.y()<q.y()?p:q).move(0,2,0))))continue;
                if(towardPlatform.size()==MAX_VISITED){complete=true;return;}
                towardPlatform.put(q,p);todo.add(q);
            }
            if(todo.isEmpty())complete=true;
        }
    }
    private boolean exteriorDoor(Cell p) {
        var b=layout.body();return p.y()==layout.groundY() && ((p.x()==b.minX()-3 || p.x()==b.maxX()+3) && p.z()>=b.minZ() && p.z()<=b.maxZ()
                || (p.z()==b.minZ()-3 || p.z()==b.maxZ()+3) && p.x()>=b.minX() && p.x()<=b.maxX());
    }
    private Type at(Cell p){return scan.contains(p.x(),p.y(),p.z())?world.at(p):Type.BLOCKED;}
    private static boolean passable(Type t){return t==Type.AIR || t==Type.PASSABLE;}
    private boolean standable(Cell p){return at(p.move(0,-1,0))==Type.SUPPORT && (passable(at(p)) || at(p)==Type.FLOOR_DECOR) && passable(at(p.move(0,1,0)));}
}
