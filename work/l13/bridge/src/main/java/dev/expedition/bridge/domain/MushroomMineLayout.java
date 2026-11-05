package dev.expedition.bridge.domain;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import java.util.*;

/** Eight original sparse cubes, at their actual native transforms. Background stays BELOW all omitted cells. */
public record MushroomMineLayout(BlockBounds body,Cell platform) {
    public static final String TARGET="dungeons_arise:mushroom_mines",PREFIX="dungeons_arise:mushroom_mines/mushroom_mines_";
    private static final int[][] OFFSETS={{0,0,0},{32,0,0},{32,0,32},{0,0,32},{0,32,0},{32,32,0},{32,32,32},{0,32,32}};
    public record Piece(String template,Cell origin,int rotation) {
        public Piece {if(rotation<0 || rotation>3)throw new IllegalArgumentException("Invalid mine rotation");}
        public Cell at(int x,int y,int z) {
            var p=switch(rotation){case 0->new Cell(x,y,z);case 1->new Cell(-z,y,x);case 2->new Cell(-x,y,-z);default->new Cell(z,y,-x);};
            return p.move(origin.x(),origin.y(),origin.z());
        }
        public int part(){return template.matches(PREFIX+"part_[0-7]")?template.charAt(template.length()-1)-'0':-1;}
        public BlockBounds bounds(){var b=at(31,31,31);return new BlockBounds(Math.min(origin.x(),b.x()),origin.y(),Math.min(origin.z(),b.z()),Math.max(origin.x(),b.x()),b.y(),Math.max(origin.z(),b.z()));}
    }
    public record Joint(int x,int y,int z,String orientation,String name,String target,String pool,String alignment){}
    public static List<Joint> joints(int part) {
        if(part<0 || part>7)throw new IllegalArgumentException("Not a main mine cube");
        String suffix=part>=4?"_top":"",pool=part>=4?"top":"main";var result=new ArrayList<Joint>();
        switch(part%4) {
            case 0->{result.add(side(0,0,31,"south_up",7,6,suffix,pool));result.add(side(31,0,0,"east_up",1,0,suffix,pool));}
            case 1->{result.add(side(0,0,0,"west_up",0,1,suffix,pool));result.add(side(31,0,31,"south_up",3,2,suffix,pool));}
            case 2->{result.add(side(0,0,31,"west_up",5,4,suffix,pool));result.add(side(31,0,0,"north_up",2,3,suffix,pool));}
            case 3->{result.add(side(0,0,0,"north_up",6,7,suffix,pool));result.add(side(31,0,31,"east_up",4,5,suffix,pool));}
        }
        if(part==0)result.add(new Joint(0,31,0,"up_north","side_upwards_0","side_upwards_1","top","aligned"));
        if(part==4)result.add(new Joint(0,0,0,"down_north","side_upwards_1","side_upwards_0","main","aligned"));
        return List.copyOf(result);
    }
    private static Joint side(int x,int y,int z,String orientation,int name,int target,String suffix,String pool){return new Joint(x,y,z,orientation,"side_"+name+suffix,"side_"+target+suffix,pool,"rollable");}
    public MushroomMineLayout {
        if(body.width()!=64 || body.height()!=64 || body.depth()!=64 || !body.contains(platform.x(),platform.y(),platform.z()) || platform.y()!=body.minY()+11)
            throw new IllegalArgumentException("Unaudited sparse mine geometry");
    }
    public int groundY(){return body.minY();}
    public MushroomMineLayout move(int x,int y,int z){return new MushroomMineLayout(body.move(x,y,z),platform.move(x,y,z));}
    public static void validateMode(String target,DungeonSpec.EntryMode mode) {
        if(target.equals(TARGET)!=(mode==DungeonSpec.EntryMode.START_MINE))throw new IllegalArgumentException("Mushroom mines require their dedicated sparse surface approach");
    }
    public static MushroomMineLayout fromPieces(List<Piece> pieces,BlockBounds complete) {
        if(pieces.isEmpty() || pieces.get(0).part()<0 || pieces.get(0).part()>3 || new HashSet<>(pieces).size()!=pieces.size())throw new IllegalArgumentException("Missing original mine root or duplicate pieces");
        var parts=new Piece[8];
        for(var p:pieces) {
            int i=p.part();
            if(i>=0){if(parts[i]!=null)throw new IllegalArgumentException("Duplicate mine cube");parts[i]=p;}
            else if(!Set.of(PREFIX+"skeleton_ranged_0",PREFIX+"zombie_armored_0",PREFIX+"zombie_armored_1").contains(p.template()))throw new IllegalArgumentException("Unknown mine piece");
        }
        if(Arrays.stream(parts).anyMatch(Objects::isNull))throw new IllegalArgumentException("Incomplete mine: all eight original cubes are required");
        var root=parts[0];BlockBounds body=null;
        for(int i=0;i<8;i++) {
            int[] offset=OFFSETS[i];var p=parts[i];
            if(p.rotation()!=root.rotation() || !p.origin().equals(root.at(offset[0],offset[1],offset[2])))throw new IllegalArgumentException("Disconnected or misrotated mine cube");
            body=body==null?p.bounds():IslandPlan.union(body,p.bounds());
        }
        if(!body.equals(complete))throw new IllegalArgumentException("Native mine bounds changed or a piece escaped the original body");
        return new MushroomMineLayout(body,parts[2].at(21,11,3));
    }
}
