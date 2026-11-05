package dev.expedition.bridge.domain;

import java.util.*;

/** Incremental, deterministic shore-to-low-deck search. It never removes blocks or routes through closed machinery. */
public final class BoardingRouteSearch {
    public enum Block { AIR, WATER, SUPPORT, BLOCKED }
    public record Cell(int x,int y,int z) {
        public Cell move(int dx,int dy,int dz){return new Cell(Math.addExact(x,dx),Math.addExact(y,dy),Math.addExact(z,dz));}
    }
    @FunctionalInterface public interface Probe { Block at(Cell cell); }
    public record Plan(List<Cell> feet,List<Cell> additions,int deckCells) {
        public Plan {feet=List.copyOf(feet);additions=List.copyOf(additions);}
        public Cell shore(){return feet.get(0);}
        public Cell landing(){return feet.get(feet.size()-1);}
    }
    // Technical work/geometry bounds, not difficulty settings. Unsupported routes fail explicitly.
    public static final int MAX_RISE=16,MAX_RUN=128,MIN_DECK_CELLS=8,MAX_DECK_CELLS=256;
    private final IslandPlan island;
    private final BlockBounds reservation;
    private final Probe world;
    private int rise,side,lane,run=3;
    private boolean complete;
    private Plan plan;
    public BoardingRouteSearch(IslandPlan island,BlockBounds reservation,Probe world) {
        if(island.surface()!=IslandPlan.Surface.WATER)throw new IllegalArgumentException("Boarding needs water and a dry bank");
        if(!reservation.contains(island.completeBounds()))throw new IllegalArgumentException("Unreserved island");
        this.island=island;this.reservation=reservation;this.world=Objects.requireNonNull(world);
    }
    public boolean complete(){return complete;}
    public Optional<Plan> result(){return Optional.ofNullable(plan);}
    /** One candidate is a bounded ray plus a small deck check, so the caller can stop between candidates. */
    public void step(int candidates) {
        if(candidates<=0)throw new IllegalArgumentException("Invalid search budget");
        while(candidates-->0 && !complete) {
            var shore=shore();int dx=side==0?-1:side==1?1:0,dz=side==2?1:side==3?-1:0;
            var target=shore.move(dx*run,rise,dz*run);
            if(inBuildingXZ(target) && standable(target)) {
                int deck=deckCells(target);
                if(deck>=MIN_DECK_CELLS)plan=connect(shore,target,dx,dz,run,deck);
            }
            if(plan!=null)complete=true;else advance();
        }
    }
    private Cell shore() {
        var b=island.building();int size=side<2?b.depth():b.width();
        int center=(size-1)/2,offset=lane==0?0:(lane%2==1?(lane+1)/2:-(lane/2));
        int along=(side<2?b.minZ():b.minX())+center+offset;
        return switch(side) {
            case 0 -> new Cell(b.maxX()+3,island.firstFreeY(),along);
            case 1 -> new Cell(b.minX()-3,island.firstFreeY(),along);
            case 2 -> new Cell(along,island.firstFreeY(),b.minZ()-3);
            default -> new Cell(along,island.firstFreeY(),b.maxZ()+3);
        };
    }
    private void advance() {
        var b=island.building();int span=side<2?b.width():b.depth(),lanes=side<2?b.depth():b.width();
        if(++run<=Math.min(MAX_RUN,span+2))return;
        run=3;if(++lane<lanes)return;
        lane=0;if(++side<4)return;
        side=0;if(++rise>MAX_RISE)complete=true;
    }
    private Plan connect(Cell shore,Cell target,int dx,int dz,int distance,int deck) {
        if(rise>distance || !standable(shore))return null;
        var feet=new ArrayList<Cell>();var additions=new LinkedHashSet<Cell>();
        for(int i=0;i<=distance;i++) {
            var p=shore.move(dx*i,Math.min(i,rise),dz*i);
            if(!clear(p) || !clear(p.move(0,1,0)))return null;
            if(i>0 && !headroom(feet.get(i-1),p))return null;
            var floor=p.move(0,-1,0);var block=at(floor);
            if(i==distance) {if(!p.equals(target) || block!=Block.SUPPORT)return null;}
            else if(block==Block.AIR || block==Block.WATER)additions.add(floor);
            else if(block!=Block.SUPPORT)return null;
            feet.add(p);
        }
        // Widen only new supports where both standing blocks are empty. Narrow ends preserve original railings.
        for(var p:feet.subList(1,feet.size()-1))for(int sign:new int[]{-1,1}) {
            var extra=p.move(-dz*sign,-1,dx*sign);
            if((at(extra)==Block.AIR || at(extra)==Block.WATER) && clear(extra.move(0,1,0)) && clear(extra.move(0,2,0)))
                additions.add(extra);
        }
        return new Plan(feet,new ArrayList<>(additions),deck);
    }
    private int deckCells(Cell start) {
        var seen=new HashSet<Cell>();var todo=new ArrayDeque<Cell>();seen.add(start);todo.add(start);boolean reachesCore=inDeckCore(start);
        while(!todo.isEmpty() && (seen.size()<MIN_DECK_CELLS || !reachesCore)) {
            var p=todo.remove();
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int dy:new int[]{0,-1,1}) {
                var q=p.move(d[0],dy,d[1]);
                if(q.y()<island.firstFreeY() || q.y()>island.firstFreeY()+MAX_RISE || !inBuildingXZ(q))continue;
                if(seen.size()<MAX_DECK_CELLS && !seen.contains(q) && standable(q) && headroom(p,q)) {
                    seen.add(q);todo.add(q);reachesCore|=inDeckCore(q);
                }
            }
        }
        return reachesCore?seen.size():0;
    }
    /** A sizeable decorative beam alone is insufficient: its original walkable component must reach the hull interior. */
    private boolean inDeckCore(Cell p) {
        var b=island.building();int insetX=b.width()/4,insetZ=b.depth()/4;
        return p.x()>=b.minX()+insetX && p.x()<=b.maxX()-insetX && p.z()>=b.minZ()+insetZ && p.z()<=b.maxZ()-insetZ;
    }
    private boolean inBuildingXZ(Cell p){var b=island.building();return p.x()>=b.minX()&&p.x()<=b.maxX()&&p.z()>=b.minZ()&&p.z()<=b.maxZ();}
    private Block at(Cell p){return reservation.contains(p.x(),p.y(),p.z())?world.at(p):Block.BLOCKED;}
    private boolean clear(Cell p){return at(p)==Block.AIR;}
    private boolean standable(Cell p){return at(p.move(0,-1,0))==Block.SUPPORT && clear(p) && clear(p.move(0,1,0));}
    private boolean headroom(Cell a,Cell b) {
        return a.y()==b.y() || clear((a.y()<b.y()?a:b).move(0,2,0));
    }
}
