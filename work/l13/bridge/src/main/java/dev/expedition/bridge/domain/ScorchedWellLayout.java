package dev.expedition.bridge.domain;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import java.util.*;

/** Original lava well, all four hub quarters, and the native selected underground branches. */
public record ScorchedWellLayout(Piece well,List<Cell> lava,Cell shaftBottom) {
    public static final String TARGET="dungeons_arise:scorched_mines",PREFIX="dungeons_arise:scorched_mines/scorched_mines_";
    public static final Set<String> SUFFIXES=Set.of("well_0","hub_part_0","hub_part_1","hub_part_2","hub_part_3",
            "barrels_0","barrels_1","corridor_0_normal","corridor_1_spawner","corridor_2_temptation","corridor_3_spawner",
            "crossing_0","crossing_1","crossing_2","crossing_3","housing_0","stairway_0","treasure_0","spawner_0","spawner_1","spawner_2");
    public record Piece(String template,Cell origin,int rotation,BlockBounds bounds,List<Cell> lava) {
        public Piece {lava=List.copyOf(lava);if(rotation<0 || rotation>3)throw new IllegalArgumentException("Invalid well rotation");}
        public Cell at(int x,int y,int z){var p=switch(rotation){case 0->new Cell(x,y,z);case 1->new Cell(-z,y,x);case 2->new Cell(-x,y,-z);default->new Cell(z,y,-x);};return p.move(origin.x(),origin.y(),origin.z());}
        public BlockBounds expectedBounds(int x,int y,int z){var b=at(x-1,y-1,z-1);return new BlockBounds(Math.min(origin.x(),b.x()),origin.y(),Math.min(origin.z(),b.z()),Math.max(origin.x(),b.x()),b.y(),Math.max(origin.z(),b.z()));}
        public Piece move(int x,int y,int z){return new Piece(template,origin.move(x,y,z),rotation,bounds.move(x,y,z),lava.stream().map(p->p.move(x,y,z)).toList());}
    }
    public ScorchedWellLayout {lava=List.copyOf(lava);if(lava.isEmpty())throw new IllegalArgumentException("Original lava missing");}
    public int groundY(){return well.at(0,13,0).y();}
    public Cell arrival(){return well.at(14,13,5);}
    public Cell rim(){return well.at(8,14,5);}
    public Cell mouth(){return well.at(5,14,5);}
    public List<Cell> approach(){var p=new ArrayList<Cell>();for(int x=14;x>=9;x--)p.add(well.at(x,13,5));p.add(rim());return List.copyOf(p);}
    public ScorchedWellLayout move(int x,int y,int z){return new ScorchedWellLayout(well.move(x,y,z),lava.stream().map(p->p.move(x,y,z)).toList(),shaftBottom.move(x,y,z));}
    public static void validateMode(String target,DungeonSpec.EntryMode mode){if(target.equals(TARGET)!=(mode==DungeonSpec.EntryMode.START_WELL))throw new IllegalArgumentException("Scorched mines require their original lava-well approach");}
    public static ScorchedWellLayout fromPieces(List<Piece> pieces) {
        if(pieces.isEmpty() || !pieces.get(0).template().equals(PREFIX+"well_0") || new HashSet<>(pieces).size()!=pieces.size())throw new IllegalArgumentException("Missing well root or duplicate native pieces");
        var root=pieces.get(0);var hubs=new Piece[4];int wells=0;var lava=new ArrayList<Cell>();
        for(var p:pieces) {
            if(!p.template().startsWith(PREFIX) || !SUFFIXES.contains(p.template().substring(PREFIX.length())))throw new IllegalArgumentException("Unknown scorched mine piece");
            if(p.template().equals(PREFIX+"well_0"))wells++;
            if(p.template().startsWith(PREFIX+"hub_part_")){int i=p.template().charAt(p.template().length()-1)-'0';if(hubs[i]!=null)throw new IllegalArgumentException("Duplicate hub quarter");hubs[i]=p;}
            if(p!=root && p.bounds().maxY()>root.origin().y()+8)throw new IllegalArgumentException("Underground piece lacks rock cover beneath the well approach");
            for(var c:p.lava())if(!p.bounds().contains(c.x(),c.y(),c.z()))throw new IllegalArgumentException("Lava escaped original piece");
            lava.addAll(p.lava());
        }
        if(wells!=1 || Arrays.stream(hubs).anyMatch(Objects::isNull) || root.lava().size()!=108 || !root.bounds().equals(root.expectedBounds(12,20,12)))throw new IllegalArgumentException("Incomplete or changed original lava well/hub");
        int[][] offsets={{-19,-26,-19},{5,-26,-19},{5,-26,5},{-19,-26,5}};
        for(int i=0;i<4;i++) {
            var h=hubs[i];int[] o=offsets[i];int x=(i==1||i==2)?25:24,z=i>=2?25:24;
            if(h.rotation()!=root.rotation() || !h.origin().equals(root.at(o[0],o[1],o[2])) || !h.bounds().equals(h.expectedBounds(x,26,z)) || h.lava().size()!=43)
                throw new IllegalArgumentException("Disconnected or changed original hub quarter");
        }
        if(new HashSet<>(lava).size()!=lava.size())throw new IllegalArgumentException("Overlapping original lava");
        return new ScorchedWellLayout(root,lava,hubs[2].at(0,3,0));
    }
}
