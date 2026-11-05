package dev.expedition.bridge.domain;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import java.util.*;

/** Geometry of the actual selected native ladder chain and BOTH hull halves. Never rolls new pieces. */
public record BlimpLadderLayout(Cell foot,Cell top,Cell landing,int dx,int dz) {
    public static final String TARGET="dungeons_arise:small_blimp";
    public static final String PREFIX="dungeons_arise:small_blimp/";
    public static final String LADDER=PREFIX+"small_blimp_ladders_0", HULL0=PREFIX+"small_blimp_part_0", HULL1=PREFIX+"small_blimp_part_1";
    public record Piece(String template,Cell origin,int rotation) {
        public Piece {if(rotation<0 || rotation>3)throw new IllegalArgumentException("Invalid rotation");}
        public Cell at(int x,int y,int z) {
            var p=switch(rotation){case 0->new Cell(x,y,z);case 1->new Cell(-z,y,x);case 2->new Cell(-x,y,-z);default->new Cell(z,y,-x);};
            return p.move(origin.x(),origin.y(),origin.z());
        }
    }
    public BlimpLadderLayout {
        if(Math.abs(dx)+Math.abs(dz)!=1 || foot.x()!=top.x() || foot.z()!=top.z() || (long)top.y()-foot.y()<52
                || !landing.equals(top.move(-dx,1,-dz)))throw new IllegalArgumentException("Invalid original ladder route");
    }
    public BlimpLadderLayout move(int x,int y,int z){return new BlimpLadderLayout(foot.move(x,y,z),top.move(x,y,z),landing.move(x,y,z),dx,dz);}
    public Cell arrival(){return foot.move(3*dx,0,3*dz);}
    public static void validateMode(String target,DungeonSpec.EntryMode mode) {
        if(target.equals(TARGET)!=(mode==DungeonSpec.EntryMode.START_LADDER))
            throw new IllegalArgumentException("Small blimp requires its dedicated original ladder entry; other structures cannot use it");
    }
    public static BlimpLadderLayout fromPieces(List<Piece> pieces,BlockBounds complete) {
        if(pieces.isEmpty() || !pieces.get(0).template().equals(LADDER) || new HashSet<>(pieces).size()!=pieces.size())
            throw new IllegalArgumentException("Missing original ladder root or duplicate pieces");
        var ladders=pieces.stream().filter(p->p.template().equals(LADDER)).sorted(Comparator.comparingInt(p->p.origin().y())).toList();
        var hulls0=pieces.stream().filter(p->p.template().equals(HULL0)).toList();
        var hulls1=pieces.stream().filter(p->p.template().equals(HULL1)).toList();
        if(hulls0.size()!=1 || hulls1.size()!=1)throw new IllegalArgumentException("Both original hull halves are required");
        for(var p:pieces)if(!Set.of(LADDER,HULL0,HULL1).contains(p.template())
                && !p.template().matches("dungeons_arise:small_blimp/small_blimp_skeleton_[0-2]"))
            throw new IllegalArgumentException("Unaudited blimp piece "+p.template());
        var first=ladders.get(0);var hull=hulls0.get(0);var other=hulls1.get(0);
        for(int i=0;i<ladders.size();i++) {
            var p=ladders.get(i);
            if(p.rotation()!=first.rotation() || !p.origin().equals(first.origin().move(0,Math.multiplyExact(i,48),0)))
                throw new IllegalArgumentException("Disconnected, overlapping or rotated original ladder chain");
        }
        if(hull.rotation()!=first.rotation() || other.rotation()!=first.rotation()
                || !hull.at(27,0,11).equals(first.origin().move(0,Math.multiplyExact(ladders.size(),48),0))
                || !other.at(0,0,0).equals(hull.at(31,0,0)))
            throw new IllegalArgumentException("Original ladder or hull connection is missing");
        var face=first.at(0,0,-1);var foot=first.origin();
        var result=new BlimpLadderLayout(foot,hull.at(27,4,11),hull.at(27,5,12),face.x()-foot.x(),face.z()-foot.z());
        if(complete.minY()!=foot.y() || !complete.contains(result.top().x(),result.top().y(),result.top().z())
                || !complete.contains(result.landing().x(),result.landing().y(),result.landing().z()))
            throw new IllegalArgumentException("Ground at ladder foot would intersect the original building");
        return result;
    }
}
