package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.domain.InteriorSiteSearch;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.Direction;
import java.util.*;

/** Prefer original ground access; a supported five-cell scaffold can cross the original stair-room window. */
final class MonasteryRoute {
    interface Probe extends InteriorSiteSearch.Probe {
        boolean window(Cell p,Direction facing);
        boolean feature(MonasteryLayout.Feature f);
        boolean scaffold(Cell p,int distance);
        void addScaffold(Cell p,int distance);
    }
    private static final int[][] ADDITIONS={{9,3,1,0},{9,4,1,0},{9,3,4,0},{9,4,4,0},{9,4,2,1}};
    private static final Set<String> GARDEN_PLANTS=Set.of("minecraft:wheat","minecraft:carrots","minecraft:potatoes","minecraft:beetroots","minecraft:allium","minecraft:cornflower","minecraft:grass");
    static boolean gardenPlant(String id){return GARDEN_PLANTS.contains(id);}
    private final MonasteryLayout layout;
    private final IslandPlan island;
    private final Probe world;
    private final List<Cell> exits;
    private final Set<Cell> remaining,seen=new HashSet<>();
    private final ArrayDeque<Cell> queue=new ArrayDeque<>();
    private final int maxFloor;
    private boolean crossing,connected;
    private int checkedExits,placed;
    MonasteryRoute(MonasteryLayout layout,IslandPlan island,int count,Probe world){
        this.layout=layout;this.island=island;this.world=world;
        if(island.firstFreeY()!=layout.groundY())throw new IllegalStateException("Monastery floor is off grade");
        exits=island.perimeterSites(count);remaining=new HashSet<>(layout.approaches());remaining.addAll(exits);
        // Original garden beds rise above the lower room floor; do not flatten them for access.
        maxFloor=Math.max(layout.groundY()+2,remaining.stream().mapToInt(Cell::y).max().orElseThrow());
    }
    private Cell at(int x,int y,int z){return layout.stairRoom().at(x,y,z);}
    private boolean inColumn(Cell p){for(int z:new int[]{1,4}){var c=at(9,3,z);if(p.x()==c.x()&&p.z()==c.z())return true;}return false;}
    private boolean clear(Cell p){var type=world.at(p);return type==Type.AIR||type==Type.PASSABLE;}
    private boolean walkable(Cell p){return !inColumn(p)&&world.at(p.move(0,-1,0))==Type.SUPPORT&&clear(p)&&clear(p.move(0,1,0));}
    private void visit(Cell p){if(p.x()<island.coreMinX()||p.x()>island.coreMaxX()||p.z()<island.coreMinZ()||p.z()>island.coreMaxZ()||p.y()<layout.groundY()||p.y()>maxFloor)return;
        if(!seen.contains(p)&&walkable(p)){seen.add(p);queue.add(p);}}
    private void neighbors(Cell p){
        for(var d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int dy:new int[]{0,1,-1}){
            var q=p.move(d[0],dy,d[1]);var lower=dy>0?p:q;
            if(dy!=0&&(!clear(lower.move(0,2,0))||!clear(lower.move(0,3,0))))continue;
            visit(q);
        }
    }
    private boolean usableWindow(){
        for(int z:new int[]{1,4})if(world.at(at(9,2,z))!=Type.SUPPORT)return false;
        for(var a:ADDITIONS){var p=at(a[0],a[1],a[2]);if(world.at(p)!=Type.AIR&&!world.scaffold(p,a[3]))return false;}
        if(!world.window(at(9,4,3),layout.stairRoom().rotation().rotate(Direction.EAST)))return false;
        for(int z=1;z<=4;z++)for(int y=5;y<=7;y++)if(world.at(at(9,y,z))!=Type.AIR)return false;
        return walkable(at(9,3,0))&&walkable(at(8,3,4));
    }
    private void features(){for(var f:layout.features())if(!world.feature(f))throw new IllegalStateException("Changed original monastery container or garden at "+f.pos());}
    boolean step(){
        if(checkedExits<exits.size()){
            if(!InteriorSiteSearch.clearPlatform(exits.get(checkedExits++),world))throw new IllegalStateException("Blocked monastery perimeter exit");
            if(checkedExits==exits.size())visit(exits.get(0));return false;
        }
        if(!connected){
            var p=queue.poll();
            if(p==null){
                if(crossing||!seen.contains(at(9,3,0))||seen.contains(at(8,3,4))||!usableWindow())throw new IllegalStateException("No safe route to all monastery entrances: remaining="+remaining);
                crossing=true;visit(at(8,3,4));return false;
            }
            remaining.remove(p);
            if(!remaining.isEmpty()){
                neighbors(p);return false;
            }
            connected=true;features();
        }
        if(crossing){
            if(!usableWindow())throw new IllegalStateException("Monastery window access changed during construction");
            if(placed<ADDITIONS.length){var a=ADDITIONS[placed++];world.addScaffold(at(a[0],a[1],a[2]),a[3]);return false;}
            for(var a:ADDITIONS)if(!world.scaffold(at(a[0],a[1],a[2]),a[3]))throw new IllegalStateException("Incomplete monastery window scaffold");
        }
        features();return true;
    }
}
