package dev.expedition.bridge.domain;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import java.util.*;

/** Matches only positioned original bridge joints directly outside the starting room's three gates. */
public record MechanicalBridgeLayout(List<Approach> approaches) {
    public static final String PREFIX="dungeons_arise:mechanical_nest/";
    public static final String ROOT=PREFIX+"mechanical_nest_start_0";
    public static final String ROOT_NAME="dungeons_arise:mechanical_nest_roofed_bridge_0";
    public static final String BRIDGE_NAME="dungeons_arise:mechanical_nest_roofed_bridge_1";
    private static final Set<String> BRIDGES=Set.of("0","1","3","5","6","7","8","terminator_2");
    public record Joint(Cell pos,int dx,int dz,String name,String target,String pool,String finalState) {
        public Joint {if(Math.abs(dx)+Math.abs(dz)!=1)throw new IllegalArgumentException("Expected horizontal joint");}
        public Joint move(int x,int y,int z){return new Joint(pos.move(x,y,z),dx,dz,name,target,pool,finalState);}
    }
    public record Piece(String template,BlockBounds bounds,List<Joint> joints) {
        public Piece {joints=List.copyOf(joints);}
    }
    public record Approach(BlockBounds bridge,Joint gate,Cell bridgeJoint) {
        public Approach {
            if(!gate.pos().move(gate.dx(),0,gate.dz()).equals(bridgeJoint)
                    || !bridge.contains(bridgeJoint.x(),bridgeJoint.y(),bridgeJoint.z()) || !outside(bridge,gate))
                throw new IllegalArgumentException("Bridge must be immediately outside the gate plane");
        }
        public Approach move(int x,int y,int z){return new Approach(bridge.move(x,y,z),gate.move(x,y,z),bridgeJoint.move(x,y,z));}
        private static boolean outside(BlockBounds b,Joint g) {
            return g.dx()==-1?b.maxX()<g.pos().x():g.dx()==1?b.minX()>g.pos().x():
                    g.dz()==-1?b.maxZ()<g.pos().z():b.minZ()>g.pos().z();
        }
    }
    public MechanicalBridgeLayout {approaches=List.copyOf(approaches);if(approaches.isEmpty() || approaches.size()>3)throw new IllegalArgumentException("No supported original bridge outside the gates");}
    public MechanicalBridgeLayout move(int x,int y,int z){return new MechanicalBridgeLayout(approaches.stream().map(a->a.move(x,y,z)).toList());}
    public static MechanicalBridgeLayout fromPieces(List<Piece> pieces) {
        if(pieces.isEmpty() || !pieces.get(0).template().equals(ROOT))throw new IllegalArgumentException("Unknown mechanical starting template");
        var root=pieces.get(0);var b=root.bounds();
        if(b.width()!=31 || b.depth()!=31 || b.height()!=48)throw new IllegalArgumentException("Changed mechanical root geometry");
        var gates=root.joints().stream().filter(j->j.name().equals(ROOT_NAME) && j.target().equals(BRIDGE_NAME)
                && j.pool().equals(PREFIX+"mechanical_nest_bridges") && j.finalState().equals("minecraft:air")).toList();
        if(gates.size()!=3 || gates.stream().map(Joint::pos).distinct().count()!=3)throw new IllegalArgumentException("Expected three original gate joints");
        var approaches=new ArrayList<Approach>();
        for(var gate:gates.stream().sorted(Comparator.comparingInt((Joint j)->j.pos().x()).thenComparingInt(j->j.pos().z())).toList()) {
            var p=gate.pos();
            boolean edge=gate.dx()==-1?p.x()==b.minX() && p.z()==b.minZ()+15:
                    gate.dx()==1?p.x()==b.maxX() && p.z()==b.minZ()+15:
                    gate.dz()==-1?p.z()==b.minZ() && p.x()==b.minX()+15:p.z()==b.maxZ() && p.x()==b.minX()+15;
            if(!edge || p.y()!=b.minY()+23)throw new IllegalArgumentException("Changed original gate position");
            Approach found=null;
            for(int i=1;i<pieces.size();i++) {
                var piece=pieces.get(i);
                for(var joint:piece.joints()) {
                    if(!joint.pos().equals(p.move(gate.dx(),0,gate.dz())) || joint.dx()!=-gate.dx() || joint.dz()!=-gate.dz()
                            || !joint.name().equals(gate.target()))continue;
                    if(!BRIDGES.stream().anyMatch(s->piece.template().equals(PREFIX+"mechanical_nest_bridge_"+s))
                            || !(joint.finalState().equals("minecraft:air") || joint.finalState().equals("minecraft:gray_carpet"))
                            || b.intersects(piece.bounds()))throw new IllegalArgumentException("Unaudited original bridge attachment");
                    if(found!=null)throw new IllegalArgumentException("Ambiguous positioned bridge attachment");
                    found=new Approach(piece.bounds(),gate,joint.pos());
                }
            }
            if(found!=null)approaches.add(found);
        }
        return new MechanicalBridgeLayout(approaches);
    }
}
