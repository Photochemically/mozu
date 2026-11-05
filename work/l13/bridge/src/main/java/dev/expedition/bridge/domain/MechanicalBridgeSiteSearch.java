package dev.expedition.bridge.domain;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import java.util.*;

/** Read-only flat bridge traversal from the original gate approach. Never searches behind the gate or on a roof. */
public final class MechanicalBridgeSiteSearch implements RoomSiteSearch {
    private final List<MechanicalBridgeLayout.Approach> approaches;
    private final InteriorSiteSearch.Probe world;
    private final Set<Cell> seen=new HashSet<>();
    private final ArrayDeque<Cell> todo=new ArrayDeque<>();
    private int index,minX,maxX,minZ,maxZ;
    private MechanicalBridgeLayout.Approach active;
    private Cell candidate,result;
    private boolean complete;
    public MechanicalBridgeSiteSearch(MechanicalBridgeLayout layout,InteriorSiteSearch.Probe world){approaches=layout.approaches();this.world=world;}
    @Override public boolean complete(){return complete;}
    @Override public Optional<Cell> result(){return Optional.ofNullable(result);}
    public Optional<BlockBounds> selectedBridge(){return result==null?Optional.empty():Optional.of(active.bridge());}
    @Override public void step(int budget) {
        if(budget<=0)throw new IllegalArgumentException("Invalid bridge search budget");
        while(!complete && budget-->0) {
            if(active==null) {
                if(index==approaches.size()){complete=true;break;}
                active=approaches.get(index++);seen.clear();todo.clear();candidate=null;
                var gate=active.gate();var p=gate.pos();
                // The two original exterior gate cells lead to the ceiling lever; no gate blocks are touched.
                if(!standable(p,world) || !standable(p.move(-gate.dx(),0,-gate.dz()),world)
                        || !passable(world.at(p.move(0,2,0))) || !standable(active.bridgeJoint(),this::at)){active=null;continue;}
                p=active.bridgeJoint();minX=maxX=p.x();minZ=maxZ=p.z();visit(p);
            } else {
                var p=todo.remove();
                if(candidate==null && InteriorSiteSearch.clearPlatform(p,this::at))candidate=p;
                for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    var q=p.move(d[0],0,d[1]);
                    if(seen.size()>=AerialSiteSearch.MAX_COMPONENT || seen.contains(q) || !standable(q,this::at))continue;
                    visit(q);
                }
                if(candidate!=null && seen.size()>=AerialSiteSearch.MIN_COMPONENT
                        && maxX-minX+1>=AerialSiteSearch.MIN_SPAN && maxZ-minZ+1>=AerialSiteSearch.MIN_SPAN){result=candidate;complete=true;}
                else if(todo.isEmpty())active=null;
            }
        }
    }
    private void visit(Cell p){seen.add(p);todo.add(p);minX=Math.min(minX,p.x());maxX=Math.max(maxX,p.x());minZ=Math.min(minZ,p.z());maxZ=Math.max(maxZ,p.z());}
    private Type at(Cell p){return active.bridge().contains(p.x(),p.y(),p.z())?world.at(p):Type.BLOCKED;}
    private static boolean passable(Type t){return t==Type.AIR || t==Type.PASSABLE;}
    private static boolean standable(Cell p,InteriorSiteSearch.Probe probe){return probe.at(p.move(0,-1,0))==Type.SUPPORT
            && (passable(probe.at(p)) || probe.at(p)==Type.FLOOR_DECOR) && passable(probe.at(p.move(0,1,0)));}
}
