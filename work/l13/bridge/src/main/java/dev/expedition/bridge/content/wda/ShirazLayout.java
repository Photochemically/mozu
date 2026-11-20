package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import java.util.*;
import java.util.function.BooleanSupplier;

/** All 27 original palace cubes, in three complete tiers, plus native selected encounters. */
record ShirazLayout(BlockPos origin,Rotation rotation,List<Feature> features) implements PerimeterLayout {
    static final String TARGET="dungeons_arise:shiraz_palace",PREFIX=TARGET+"/shiraz_palace_",POOL="dungeons_arise:desertic/shiraz_palace/shiraz_palace_";
    static final int[][] GRID={{0,0},{48,0},{96,0},{96,48},{48,48},{0,48},{0,96},{48,96},{96,96}};
    static final Set<String> ENCOUNTERS=Set.of("skeleton_elite_0","skeleton_garden_1","skeleton_garden_exterior_1","skeleton_illusion_0","skeleton_library_0","skeleton_tower_1","spider_0","spider_1","axe_illusion_2","crossbow_illusion_1","husk_garden_0","husk_garden_2","husk_garden_exterior_0","husk_tower_0");
    record Socket(int owner,BlockPos pos,Direction front,Direction top,String name,String target,String pool,boolean aligned) {}
    record Feature(Cell pos,String block,CompoundTag data) {}
    static String key(WdaTemplatePiece p){if(!p.id().startsWith(PREFIX))throw new IllegalArgumentException("Foreign palace component");return p.id().substring(PREFIX.length());}
    static String pool(String k){if(k.matches("part_(?:[0-9]|1[0-9]|2[0-6])"))return POOL+"main";if(ENCOUNTERS.contains(k))return POOL+"spawners";throw new IllegalArgumentException("Unknown or unenrolled palace component "+k);}
    static CompoundTag stableData(String block,CompoundTag n){
        var result=new CompoundTag();
        if(block.equals("minecraft:water")||block.equals("minecraft:lava"))result.putInt("level",n.getInt("level"));
        else if(block.equals("minecraft:spawner")) {
            result.put("SpawnData",n.getCompound("SpawnData").copy());result.put("SpawnPotentials",n.getList("SpawnPotentials",10).copy());
            for(var f:List.of("MinSpawnDelay","MaxSpawnDelay","SpawnCount","MaxNearbyEntities","RequiredPlayerRange","SpawnRange"))result.putInt(f,n.getInt(f));
        }else if(n.contains("LootTable"))result.putString("LootTable",n.getString("LootTable"));
        else result.put("Items",n.getList("Items",10).copy());
        return result;
    }
    static ShirazLayout inspect(List<WdaTemplatePiece> pieces,WdaStructurePlanner.Environment env,List<Socket> sockets,List<Feature> features){
        if(env.entryMode()!=DungeonSpec.EntryMode.PERIMETER||env.boarding()||(env.surface()!=IslandPlan.Surface.GRASS&&env.surface()!=IslandPlan.Surface.ROCK))throw new IllegalArgumentException("Palace requires a dry perimeter");
        if(pieces.isEmpty()||!key(pieces.get(0)).equals("part_13")||pieces.get(0).origin().getY()!=env.firstFreeY()+15)throw new IllegalArgumentException("Changed palace native root");
        var root=pieces.get(0);var r=root.rotation();var origin=new BlockPos(-48,-32,-48).rotate(r).offset(root.origin());
        var shell=new BitSet(27);var unique=new HashSet<String>();int encounters=0;
        for(var p:pieces){String k=key(p);pool(k);if(p.terrainMatching()||!unique.add(k+"@"+p.origin()))throw new IllegalArgumentException("Projected or duplicate palace component");
            if(k.startsWith("part_")){int i=Integer.parseInt(k.substring(5));if(shell.get(i))throw new IllegalArgumentException("Duplicate palace cube");shell.set(i);var o=GRID[i%9];p.requireFrame(new BlockPos(o[0],32*(i/9),o[1]).rotate(r).offset(origin),r,48,32,48);}
            else {p.requireFrame(p.origin(),p.rotation(),1,2,1);encounters++;}
        }
        if(shell.cardinality()!=27)throw new IllegalArgumentException("Incomplete original palace shell");
        var positions=new HashMap<BlockPos,List<Socket>>();
        for(var s:sockets){if(s.owner()<0||s.owner()>=pieces.size()||s.front()==null||s.top()==null)throw new IllegalArgumentException("Invalid palace connector");positions.computeIfAbsent(s.pos(),ignored->new ArrayList<>()).add(s);}
        var edges=new HashMap<Integer,Set<Integer>>();
        for(var out:sockets){int attached=0;for(var in:positions.getOrDefault(out.pos().relative(out.front()),List.of()))if(out.owner()!=in.owner()&&out.pool().equals(pool(key(pieces.get(in.owner()))))
                &&out.target().equals(in.name())&&out.front()==in.front().getOpposite()&&(!out.aligned()||out.top()==in.top())){
            attached++;edges.computeIfAbsent(out.owner(),ignored->new HashSet<>()).add(in.owner());edges.computeIfAbsent(in.owner(),ignored->new HashSet<>()).add(out.owner());
        }
            if(out.front()==Direction.UP&&out.pool().equals(POOL+"spawners")&&attached!=1)throw new IllegalArgumentException("Missing or ambiguous original palace encounter");
        }
        var seen=new HashSet<Integer>();var queue=new ArrayDeque<Integer>();queue.add(0);while(!queue.isEmpty()){int i=queue.remove();if(seen.add(i))queue.addAll(edges.getOrDefault(i,Set.of()));}
        if(seen.size()!=pieces.size())throw new IllegalArgumentException("Disconnected palace content");
        var cells=new HashSet<Cell>();for(var f:features)if(!cells.add(f.pos()))throw new IllegalArgumentException("Overlapping palace loot or devices");
        if(features.stream().filter(f->f.block().equals("minecraft:spawner")).count()!=encounters)throw new IllegalArgumentException("Missing palace spawner data");
        return new ShirazLayout(origin,r,List.copyOf(features));
    }
    Cell at(int x,int y,int z){var p=new BlockPos(x,y,z).rotate(rotation).offset(origin);return new Cell(p.getX(),p.getY(),p.getZ());}
    int groundY(){return origin.getY()+33;}
    @Override public ShirazLayout move(int x,int y,int z){return new ShirazLayout(origin.offset(x,y,z),rotation,features.stream().map(f->new Feature(f.pos().move(x,y,z),f.block(),f.data().copy())).toList());}
    @Override public BooleanSupplier survey(ServerLevel world,IslandPlan island,int exits){return new ShirazRoute(this,island,exits,new ShirazProbe(world))::step;}
}
