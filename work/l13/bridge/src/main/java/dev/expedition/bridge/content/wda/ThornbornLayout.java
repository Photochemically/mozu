package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Native tower graph, including embedded rooms and the existing medium-terminator resource alias. */
record ThornbornLayout(int groundY,List<Landing> landings,List<Feature> features) implements PerimeterLayout {
    static final String TARGET="dungeons_arise:thornborn_towers",PREFIX=TARGET+"/thornborn_towers_",POOL="dungeons_arise:fortified/thornborn_towers/thornborn_towers_";
    static final Set<String> MAIN=Set.of("part_0_main","part_1_support","part_2_tower","roof_0","roof_1","bridge_0","bridge_1_broken");
    static final Set<String> HANGING=Set.of("hanging_big_0_main","hanging_big_0_tower","hanging_big_bottom","hanging_big_top","hanging_medium_0","hanging_medium_1","hanging_medium_2","hanging_small_0","hanging_small_1","hanging_small_2","hanging_bridge_0_small","hanging_bridge_1_small_terminator","hanging_bridge_2_medium_terminator");
    static final Set<String> ENCOUNTERS=Set.of("skeleton_juggernaut_0","skeleton_juggernaut_1","skeleton_ranged_0","witch_0","top_skeleton_juggernaut_0","top_skeleton_ranged_0","top_phantom_0");
    static final Set<String> CAPS=Set.of("roof_0","hanging_big_bottom","hanging_big_top","bridge_2_terminator","hanging_bridge_1_small_terminator","hanging_bridge_2_medium_terminator");
    record Socket(int owner,BlockPos pos,Direction front,Direction top,String name,String target,String pool,boolean aligned) {}
    record Feature(Cell pos,String block,CompoundTag data) {}
    record Landing(BlockPos origin,Rotation rotation,boolean broken) {
        Cell at(int x,int y,int z){var p=new BlockPos(x,y,z).rotate(rotation).offset(origin);return new Cell(p.getX(),p.getY(),p.getZ());}
        Cell column(){return broken?at(9,0,5):at(-1,0,0);}
        int feet(){return origin.getY()+(broken?12:2);}
        Cell approach(int ground){var c=column();var d=rotation.rotate(broken?Direction.NORTH:Direction.WEST);return new Cell(c.x()+d.getStepX(),ground,c.z()+d.getStepZ());}
    }
    static String key(WdaTemplatePiece p){if(!p.id().startsWith(PREFIX))throw new IllegalArgumentException("Foreign thornborn component");return p.id().substring(PREFIX.length());}
    static boolean accepts(String pool,String k){if(!pool.startsWith(POOL))return false;return switch(pool.substring(POOL.length())){
        case "main"->MAIN.contains(k)||CAPS.contains(k);case "hanging"->HANGING.contains(k)||CAPS.contains(k);
        case "rooms"->k.matches("room_[0-5]");case "spawners"->ENCOUNTERS.contains(k);default->false;};}
    static int[] size(String k){
        if(MAIN.contains(k)||k.matches("room_[0-5]")||k.matches("hanging_big_0_(main|tower)"))return new int[]{17,17,17};
        if(ENCOUNTERS.contains(k))return new int[]{1,2,1};
        return switch(k){case "bridge_2_terminator","hanging_bridge_2_medium_terminator"->new int[]{1,3,3};
            case "hanging_big_bottom"->new int[]{17,11,17};case "hanging_big_top"->new int[]{17,8,17};
            case "hanging_medium_0","hanging_medium_2"->new int[]{11,35,11};case "hanging_medium_1"->new int[]{11,45,11};
            case "hanging_small_0","hanging_small_1","hanging_small_2"->new int[]{9,26,9};
            case "hanging_bridge_0_small"->new int[]{3,3,3};case "hanging_bridge_1_small_terminator"->new int[]{3,2,2};
            default->throw new IllegalArgumentException("Unknown thornborn component "+k);};
    }
    static CompoundTag stableData(String block,CompoundTag n){var result=new CompoundTag();
        if(block.equals("minecraft:spawner")){result.put("SpawnData",n.getCompound("SpawnData").copy());result.put("SpawnPotentials",n.getList("SpawnPotentials",10).copy());
            for(var f:List.of("MinSpawnDelay","MaxSpawnDelay","SpawnCount","MaxNearbyEntities","RequiredPlayerRange","SpawnRange"))result.putInt(f,n.getInt(f));}
        else if(n.contains("LootTable"))result.putString("LootTable",n.getString("LootTable"));else result.put("Items",n.getList("Items",10).copy());return result;}
    static ThornbornLayout inspect(List<WdaTemplatePiece> pieces,WdaStructurePlanner.Environment env,List<Socket> sockets,List<Feature> features){
        if(env.boarding()||env.entryMode()!=DungeonSpec.EntryMode.PERIMETER||(env.surface()!=IslandPlan.Surface.GRASS&&env.surface()!=IslandPlan.Surface.ROCK))throw new IllegalArgumentException("Thornborn requires dry perimeter terrain");
        // Absolute 130 minus the original single-pool groundLevelDelta of one; independent of planning terrain.
        if(pieces.isEmpty()||!key(pieces.get(0)).equals("part_0_main")||pieces.get(0).origin().getY()!=129)throw new IllegalArgumentException("Changed thornborn native root");
        int ground=Integer.MAX_VALUE,spawners=0;var unique=new HashSet<String>();var landings=new ArrayList<Landing>();
        for(var p:pieces){var k=key(p);var s=size(k);if(p.terrainMatching()||!unique.add(k+"@"+p.origin()))throw new IllegalArgumentException("Projected or duplicate thornborn component");
            p.requireFrame(p.origin(),p.rotation(),s[0],s[1],s[2]);ground=Math.min(ground,p.bounds().minY());if(ENCOUNTERS.contains(k))spawners++;
            if(k.equals("bridge_1_broken")||k.equals("bridge_2_terminator"))landings.add(new Landing(p.origin(),p.rotation(),k.equals("bridge_1_broken")));
        }
        var positions=new HashMap<BlockPos,List<Socket>>();for(var s:sockets){if(s.owner()<0||s.owner()>=pieces.size()||s.front()==null||s.top()==null)throw new IllegalArgumentException("Invalid thornborn connector");positions.computeIfAbsent(s.pos(),ignored->new ArrayList<>()).add(s);}
        var edges=new HashMap<Integer,Set<Integer>>();for(var out:sockets){int matches=0;for(var in:positions.getOrDefault(out.pos().relative(out.front()),List.of()))
            if(out.owner()!=in.owner()&&accepts(out.pool(),key(pieces.get(in.owner())))&&out.target().equals(in.name())&&out.front()==in.front().getOpposite()&&(!out.aligned()||out.top()==in.top())){
                matches++;edges.computeIfAbsent(out.owner(),ignored->new HashSet<>()).add(in.owner());edges.computeIfAbsent(in.owner(),ignored->new HashSet<>()).add(out.owner());}
            if(matches>1)throw new IllegalArgumentException("Ambiguous thornborn connection");
            if(key(pieces.get(out.owner())).equals("part_0_main")&&out.front().getAxis().isVertical()&&matches!=1)throw new IllegalArgumentException("Incomplete main tower roof or support");
        }
        var seen=new HashSet<Integer>();var queue=new ArrayDeque<Integer>();queue.add(0);while(!queue.isEmpty()){int i=queue.remove();if(seen.add(i))queue.addAll(edges.getOrDefault(i,Set.of()));}
        if(seen.size()!=pieces.size()||landings.isEmpty())throw new IllegalArgumentException("Disconnected thornborn content or no original bridge landing");
        var cells=new HashSet<Cell>();for(var f:features)if(!cells.add(f.pos()))throw new IllegalArgumentException("Overlapping thornborn devices");
        if(features.stream().filter(f->f.block().equals("minecraft:spawner")).count()!=spawners)throw new IllegalArgumentException("Missing thornborn spawner content");
        return new ThornbornLayout(ground,List.copyOf(landings),List.copyOf(features));
    }
    @Override public ThornbornLayout move(int x,int y,int z){return new ThornbornLayout(Math.addExact(groundY,y),landings.stream().map(l->new Landing(l.origin().offset(x,y,z),l.rotation(),l.broken())).toList(),features.stream().map(f->new Feature(f.pos().move(x,y,z),f.block(),f.data().copy())).toList());}
    @Override public BooleanSupplier survey(ServerLevel world,IslandPlan island,int exits){return new ThornbornRoute(this,island,exits,new ThornbornProbe(world))::step;}
}
