package dev.expedition.bridge.domain;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import java.util.*;

/** Finds a native deck, not a new platform. Original walking area must span both axes and reach a piece edge. */
public final class AerialSiteSearch implements RoomSiteSearch {
    public static final int MIN_COMPONENT=24,MIN_SPAN=5,MAX_COMPONENT=65536,MAX_SCAN_CELLS=2_000_000;
    private final BlockBounds bounds;
    private final InteriorSiteSearch.Probe probe;
    private final List<Cell> excluded;
    private final Set<Cell> rejected=new HashSet<>(),seen=new HashSet<>();
    private final ArrayDeque<Cell> todo=new ArrayDeque<>();
    private final long scanVolume;
    private long cursor;
    private Cell candidate,result;
    private boolean edge,complete;
    private int minX,maxX,minZ,maxZ;
    public AerialSiteSearch(InteriorRoom room,InteriorSiteSearch.Probe probe,List<Cell> excluded) {
        bounds=room.bounds();this.probe=probe;this.excluded=List.copyOf(excluded);
        scanVolume=Math.multiplyExact(Math.multiplyExact((long)bounds.width(),bounds.depth()),bounds.height());
        if(scanVolume>MAX_SCAN_CELLS)throw new IllegalArgumentException("Aerial piece exceeds safe search budget; requires explicit route adaptation");
    }
    @Override public boolean complete(){return complete;}
    @Override public Optional<Cell> result(){return Optional.ofNullable(result);}
    @Override public void step(int budget) {
        if(budget<=0)throw new IllegalArgumentException("Invalid aerial search budget");
        while(!complete && budget-->0) {
            if(candidate==null) {
                if(cursor==scanVolume){complete=true;break;}
                long index=cursor++;int x=(int)(index%bounds.width());index/=bounds.width();int z=(int)(index%bounds.depth());int y=(int)(index/bounds.depth());
                var p=new Cell(bounds.minX()+centerOut(x,bounds.width()),bounds.minY()+y,bounds.minZ()+centerOut(z,bounds.depth()));
                if(rejected.contains(p) || !InteriorSiteSearch.clearPlatform(p,this::at) || excluded.stream().anyMatch(q->!InteriorSiteSearch.separated(p,q)))continue;
                candidate=p;seen.clear();todo.clear();edge=false;minX=maxX=p.x();minZ=maxZ=p.z();visit(p);
            } else {
                var p=todo.remove();
                for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int dy:new int[]{0,-1,1}) {
                    var q=p.move(d[0],dy,d[1]);
                    if(seen.size()==MAX_COMPONENT || seen.contains(q) || !standable(q))continue;
                    if(dy!=0 && !passable(at((p.y()<q.y()?p:q).move(0,2,0))))continue;
                    visit(q);
                }
                if(seen.size()>=MIN_COMPONENT && maxX-minX+1>=MIN_SPAN && maxZ-minZ+1>=MIN_SPAN && edge){result=candidate;complete=true;}
                else if(todo.isEmpty()){rejected.addAll(seen);candidate=null;}
            }
        }
    }
    private static int centerOut(int i,int size){int c=(size-1)/2;return c+(i==0?0:i%2==1?(i+1)/2:-(i/2));}
    private void visit(Cell p) {
        seen.add(p);todo.add(p);
        minX=Math.min(minX,p.x());maxX=Math.max(maxX,p.x());minZ=Math.min(minZ,p.z());maxZ=Math.max(maxZ,p.z());
        edge|=p.x()==bounds.minX() || p.x()==bounds.maxX() || p.z()==bounds.minZ() || p.z()==bounds.maxZ();
    }
    private Type at(Cell p){return bounds.contains(p.x(),p.y(),p.z())?probe.at(p):Type.BLOCKED;}
    private static boolean passable(Type t){return t==Type.AIR || t==Type.PASSABLE;}
    private boolean standable(Cell p){return at(p.move(0,-1,0))==Type.SUPPORT && (passable(at(p)) || at(p)==Type.FLOOR_DECOR) && passable(at(p.move(0,1,0)));}
}
