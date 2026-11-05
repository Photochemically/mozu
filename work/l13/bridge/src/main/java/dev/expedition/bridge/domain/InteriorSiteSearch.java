package dev.expedition.bridge.domain;

import java.util.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;

/** Finds a clear platform reachable from an original room passage. Never digs through the original shell. */
public final class InteriorSiteSearch implements RoomSiteSearch {
    @FunctionalInterface public interface Probe { Type at(Cell cell); }
    public static final int MAX_VISITED=65536;
    private final InteriorRoom room;
    private final Probe world;
    private final List<Cell> excluded;
    private final Set<Cell> seen=new HashSet<>();
    private final ArrayDeque<Cell> todo=new ArrayDeque<>();
    private boolean complete;
    private Cell result;
    public InteriorSiteSearch(InteriorRoom room,Probe world,List<Cell> excluded) {
        this.room=room;this.world=world;this.excluded=List.copyOf(excluded);
        for(var p:room.passages().stream().sorted(Comparator.comparingInt(Cell::y).thenComparingInt(Cell::x).thenComparingInt(Cell::z)).toList()) {
            // Jigsaw is an attachment marker, not necessarily feet height. Mining-system joints occupy the
            // head cell above the original rail; only accept the same open two-block column, never scan through a floor.
            for(int dy:new int[]{0,-1}) {
                var feet=p.move(0,dy,0);
                if(passable(at(p)) && standable(feet) && seen.add(feet))todo.add(feet);
            }
        }
        if(todo.isEmpty())complete=true;
    }
    public boolean complete(){return complete;}
    public Optional<Cell> result(){return Optional.ofNullable(result);}
    public void step(int budget) {
        if(budget<=0)throw new IllegalArgumentException("Invalid interior search budget");
        while(!complete && budget-->0) {
            var p=todo.remove();
            if(clearPlatform(p) && excluded.stream().allMatch(q->separated(p,q))) {result=p;complete=true;break;}
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int dy:new int[]{0,-1,1}) {
                var q=p.move(d[0],dy,d[1]);
                if(seen.size()>=MAX_VISITED || seen.contains(q) || !standable(q))continue;
                if(dy!=0 && !passable(at((p.y()<q.y()?p:q).move(0,2,0))))continue;
                seen.add(q);todo.add(q);
            }
            if(todo.isEmpty())complete=true;
        }
    }
    public static boolean separated(Cell p,Cell q) {
        return Math.abs((long)p.x()-q.x())+Math.abs((long)p.y()-q.y())+Math.abs((long)p.z()-q.z())>=8;
    }
    public static boolean clearPlatform(Cell p,Probe probe) {
        if(probe.at(p)!=Type.AIR || probe.at(p.move(0,1,0))!=Type.AIR)return false;
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
            if(probe.at(p.move(x,-1,z))!=Type.SUPPORT)return false;
            for(int y=0;y<=2;y++)if(!(y==0?feetPassable(probe.at(p.move(x,y,z))):passable(probe.at(p.move(x,y,z)))))return false;
        }
        return true;
    }
    private boolean clearPlatform(Cell p){return clearPlatform(p,this::at);}
    private static boolean passable(Type type){return type==Type.AIR || type==Type.PASSABLE;}
    private static boolean feetPassable(Type type){return passable(type) || type==Type.FLOOR_DECOR;}
    private Type at(Cell p){return room.bounds().contains(p.x(),p.y(),p.z())?world.at(p):Type.BLOCKED;}
    private boolean standable(Cell p){return at(p.move(0,-1,0))==Type.SUPPORT && feetPassable(at(p)) && passable(at(p.move(0,1,0)));}
}
