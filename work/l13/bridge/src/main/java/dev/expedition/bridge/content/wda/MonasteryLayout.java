package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.DungeonSpec;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import java.util.*;
import java.util.function.BooleanSupplier;

record MonasteryLayout(BlockPos origin,Landing stairRoom,List<Cell> approaches,List<Feature> features,List<Spawn> inhabitants) implements PerimeterLayout {
    static final String TARGET="dungeons_arise:monastery", PREFIX=TARGET+"/", POOL="dungeons_arise:fortified/monastery/";
    record Socket(int owner,BlockPos pos,Direction front,Direction top,String name,String target,String pool,boolean aligned) {}
    record Feature(Cell pos,String block,String loot,int waterLevel) {}
    record Spawn(BlockPos pos,String type) {}
    record Landing(BlockPos origin,Rotation rotation){
        Cell at(int x,int y,int z){var p=new BlockPos(x,y,z).rotate(rotation).offset(origin);return new Cell(p.getX(),p.getY(),p.getZ());}
    }
    static String key(WdaTemplatePiece p){if(!p.id().startsWith(PREFIX))throw new IllegalArgumentException("Foreign monastery piece");return p.id().substring(PREFIX.length());}
    static boolean accepts(String pool,String k){
        if(!pool.startsWith(POOL))return false;
        return switch(pool.substring(POOL.length())){
            case "monastery_rooms" -> k.matches("monastery_room_[012]|monastery_room_3_stairs");
            case "monastery_top" -> k.equals("monastery_top_0");
            case "monastery_bridges" -> k.matches("monastery_bridge_0|monastery_bridge_crossing|monastery_bridge_terminator");
            case "monastery_corridors" -> k.matches("monastery_corridor_0|monastery_corridor_crossing|monastery_corridor_terminator");
            case "monastery_bridges_deco" -> k.equals("monastery_bridges_deco_0")||k.equals("monastery_deco_empty");
            case "monastery_corridors_deco" -> k.matches("monastery_corridors_deco_[0-3]|monastery_deco_empty");
            case "villagers" -> k.matches("villager_normal_0|iron_golem_normal_0");
            default -> false;
        };
    }
    private static int[] size(String k){
        if(k.equals("monastery_start"))return new int[]{30,1,30};
        if(k.equals("monastery_top_0"))return new int[]{30,25,30};
        if(k.equals("monastery_bridges_deco_0"))return new int[]{15,12,15};
        if(k.matches("monastery_corridors_deco_[0-3]"))return new int[]{9,7,9};
        if(k.equals("monastery_deco_empty"))return new int[]{1,1,1};
        if(accepts(POOL+"villagers",k))return new int[]{1,3,1};
        if(accepts(POOL+"monastery_rooms",k)||accepts(POOL+"monastery_bridges",k)||accepts(POOL+"monastery_corridors",k))return new int[]{15,15,15};
        throw new IllegalArgumentException("Unknown monastery template "+k);
    }
    private static boolean joins(Socket out,Socket in,List<WdaTemplatePiece> pieces){return accepts(out.pool(),key(pieces.get(in.owner())))
            &&out.target().equals(in.name())&&out.front()==in.front().getOpposite()&&out.pos().relative(out.front()).equals(in.pos())
            &&(!out.aligned()||out.top()==in.top());}
    static MonasteryLayout inspect(List<WdaTemplatePiece> pieces,WdaStructurePlanner.Environment env,List<Socket> sockets,List<Feature> features){
        if(env.boarding()||env.entryMode()!=DungeonSpec.EntryMode.PERIMETER||
                (env.surface()!=IslandPlan.Surface.GRASS&&env.surface()!=IslandPlan.Surface.ROCK))throw new IllegalArgumentException("Monastery requires a dry island");
        if(pieces.isEmpty()||!key(pieces.get(0)).equals("monastery_start")||pieces.get(0).origin().getY()!=env.firstFreeY()-1)
            throw new IllegalArgumentException("Changed monastery root or height");
        var used=new HashSet<String>();var approaches=new ArrayList<Cell>();var inhabitants=new ArrayList<Spawn>();
        int starts=0,rooms=0,roofs=0,stairs=0;Landing stairRoom=null;
        for(var p:pieces){var k=key(p);var s=size(k);
            if(p.terrainMatching()||!used.add(k+"@"+p.origin()))throw new IllegalArgumentException("Projected or duplicate monastery piece");
            p.requireFrame(p.origin(),p.rotation(),s[0],s[1],s[2]);
            if(k.equals("monastery_start"))starts++;
            if(k.equals("monastery_top_0"))roofs++;
            if(accepts(POOL+"monastery_rooms",k))rooms++;
            if(k.equals("monastery_room_3_stairs")){stairs++;stairRoom=new Landing(p.origin(),p.rotation());}
            if(accepts(POOL+"monastery_rooms",k)||accepts(POOL+"monastery_bridges",k)||accepts(POOL+"monastery_corridors",k)){
                // room_2's second cell is the original irrigation channel, not a walking floor.
                var point=new Landing(p.origin(),p.rotation()).at(k.equals("monastery_room_2")?1:2,3,7);
                // The bridge crossing has two west-facing inlets, one cell apart vertically.
                // Preserve the native rise selected through either inlet instead of flattening wings.
                if(accepts(POOL+"monastery_rooms",k)&&point.y()!=pieces.get(0).origin().getY()+4)
                    throw new IllegalArgumentException("Changed monastery main floor height");
                approaches.add(point);
            }
            if(accepts(POOL+"villagers",k))inhabitants.add(new Spawn(p.origin().above(),"minecraft:"+(k.startsWith("iron_golem")?"iron_golem":"villager")));
        }
        if(starts!=1||rooms!=4||stairs!=1||roofs!=1)throw new IllegalArgumentException("Incomplete original monastery main building");
        var edges=new HashMap<Integer,Set<Integer>>();int roots=0,roofLinks=0;
        for(var s:sockets)if(s.owner()<0||s.owner()>=pieces.size()||s.front()==null||s.top()==null)throw new IllegalArgumentException("Invalid monastery connector");
        for(var out:sockets){int matches=0;for(var in:sockets)if(out.owner()!=in.owner()&&(joins(out,in,pieces)||joins(in,out,pieces))){
            matches++;edges.computeIfAbsent(out.owner(),ignored->new HashSet<>()).add(in.owner());
        }
            boolean required=out.owner()==0||key(pieces.get(out.owner())).equals("monastery_room_3_stairs")&&out.pool().equals(POOL+"monastery_top");
            if(matches>1||required&&matches!=1)throw new IllegalArgumentException("Incomplete or ambiguous monastery connection");
            if(out.owner()==0)roots++;else if(required)roofLinks++;
        }
        if(roots!=4||roofLinks!=1)throw new IllegalArgumentException("Changed monastery core sockets");
        var seen=new HashSet<Integer>();var queue=new ArrayDeque<Integer>();queue.add(0);
        while(!queue.isEmpty()){int i=queue.remove();if(seen.add(i))queue.addAll(edges.getOrDefault(i,Set.of()));}
        if(seen.size()!=pieces.size())throw new IllegalArgumentException("Detached monastery wing, decoration or inhabitant");
        return new MonasteryLayout(pieces.get(0).origin(),stairRoom,List.copyOf(approaches),List.copyOf(features),List.copyOf(inhabitants));
    }
    int groundY(){return origin.getY()+4;}
    boolean matchesEntities(List<Spawn> actual){return actual.size()==inhabitants.size()&&new HashSet<>(actual).equals(new HashSet<>(inhabitants));}
    @Override public MonasteryLayout move(int x,int y,int z){return new MonasteryLayout(origin.offset(x,y,z),new Landing(stairRoom.origin().offset(x,y,z),stairRoom.rotation()),
            approaches.stream().map(p->p.move(x,y,z)).toList(),features.stream().map(f->new Feature(f.pos().move(x,y,z),f.block(),f.loot(),f.waterLevel())).toList(),
            inhabitants.stream().map(s->new Spawn(s.pos().offset(x,y,z),s.type())).toList());}
    @Override public BooleanSupplier survey(ServerLevel world,IslandPlan island,int exits){return new MonasteryRoute(this,island,exits,new MonasteryProbe(world))::step;}
}
