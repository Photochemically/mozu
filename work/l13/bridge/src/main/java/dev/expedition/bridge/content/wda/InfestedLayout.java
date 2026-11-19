package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.DungeonSpec;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Four complete original shell tiers, twelve selected floors and their original room/encounter attachments. */
record InfestedLayout(BlockPos origin,Rotation rotation,List<Feature> features) implements PerimeterLayout {
    static final String TARGET="dungeons_arise:infested_temple", PREFIX=TARGET+"/infested_temple_", POOL="dungeons_arise:haunted/infested_temple/infested_temple_";
    static final int[][] RING={{12,0},{0,0},{51,0},{51,12},{51,51},{12,51},{0,51},{0,12}};
    static final int[][] SIZES={{39,12},{12,12},{12,12},{12,39},{12,12},{39,12},{12,12},{12,39}};
    static final String[] LAYOUTS={"layout_part_3_base","layout_part_2_middle","layout_part_1_middle","layout_part_0_top"};
    record Socket(int owner,BlockPos pos,Direction front,Direction top,String name,String target,String pool,boolean aligned) {}
    record Feature(Cell pos,String block,CompoundTag data) {}
    static String key(WdaTemplatePiece p){if(!p.id().startsWith(PREFIX))throw new IllegalArgumentException("Foreign infested temple piece");return p.id().substring(PREFIX.length());}
    static String pool(String k){
        if(Arrays.asList(LAYOUTS).contains(k))return POOL+"layout";
        if(k.matches("(?:base|main)_part_[0-7]"))return POOL+k.substring(0,k.indexOf('_'));
        if(k.matches("top_part_[0-7]")||k.equals("inner_top_0"))return POOL+"top";
        if(k.equals("inner_part_0"))return POOL+"inner";
        if(k.matches("level_[0-2]"))return POOL+"levels";
        if(k.matches("room_[0-8]"))return POOL+"rooms";
        if(k.matches("cave_spider_[01]|(?:wither_skeleton|skeleton)_0"))return POOL+"spawners";
        throw new IllegalArgumentException("Unknown infested temple component "+k);
    }
    static CompoundTag stableData(String block,CompoundTag original){
        var result=new CompoundTag();
        if(block.equals("minecraft:spawner")){
            result.put("SpawnData",original.getCompound("SpawnData").copy());result.put("SpawnPotentials",original.getList("SpawnPotentials",10).copy());
            for(var field:List.of("MinSpawnDelay","MaxSpawnDelay","SpawnCount","MaxNearbyEntities","RequiredPlayerRange","SpawnRange"))result.putInt(field,original.getInt(field));
        }else result.putString("LootTable",original.getString("LootTable"));
        // Delay runs after placement. StructureTemplate assigns a new LootTableSeed on first placement.
        // Neither is an immutable source-template field; native world persistence retains the actual seed.
        return result;
    }
    private static BlockPos at(BlockPos origin,Rotation r,int x,int y,int z){return new BlockPos(x,y,z).rotate(r).offset(origin);}
    private static void once(Set<String> slots,String slot){if(!slots.add(slot))throw new IllegalArgumentException("Duplicate infested temple slot "+slot);}
    static InfestedLayout inspect(List<WdaTemplatePiece> pieces,WdaStructurePlanner.Environment env,List<Socket> sockets,List<Feature> features){
        if(env.boarding()||env.entryMode()!=DungeonSpec.EntryMode.PERIMETER||(env.surface()!=IslandPlan.Surface.GRASS&&env.surface()!=IslandPlan.Surface.ROCK))throw new IllegalArgumentException("Infested temple requires a dry perimeter");
        if(pieces.isEmpty()||!key(pieces.get(0)).equals(LAYOUTS[2])||pieces.get(0).origin().getY()!=env.firstFreeY()+53)throw new IllegalArgumentException("Changed infested temple native root");
        var rotation=pieces.get(0).rotation();var origin=pieces.get(0).origin().below(64);var slots=new HashSet<String>();var unique=new HashSet<String>();int encounters=0;
        for(var p:pieces){String k=key(p),group=pool(k);int y=p.origin().getY()-origin.getY();
            if(p.terrainMatching()||!unique.add(k+"@"+p.origin()))throw new IllegalArgumentException("Projected or duplicate infested temple content");
            int layout=Arrays.asList(LAYOUTS).indexOf(k);
            if(layout>=0){once(slots,"layout"+layout);p.requireFrame(at(origin,rotation,0,layout*32,0),rotation,12,32,12);}
            else if(k.matches("(?:base|main|top)_part_[0-7]")){
                int tier=y/32,index=k.charAt(k.length()-1)-'0';
                if(y%32!=0||tier<0||tier>3||!k.startsWith((tier==0?"base":tier==3?"top":"main")+"_"))throw new IllegalArgumentException("Misplaced infested wall tier");
                once(slots,"wall"+tier+"/"+index);p.requireFrame(at(origin,rotation,RING[index][0],y,RING[index][1]),rotation,SIZES[index][0],32,SIZES[index][1]);
            }else if(k.equals("inner_part_0")||k.equals("inner_top_0")){
                int tier=y/32;if(y%32!=0||tier<0||tier>3||(tier==3)!=k.equals("inner_top_0"))throw new IllegalArgumentException("Misplaced infested interior");
                once(slots,"inner"+tier);p.requireFrame(at(origin,rotation,12,y,12),rotation,39,32,39);
            }else if(group.equals(POOL+"levels")){
                if(y<0||y>=96||y%8!=0)throw new IllegalArgumentException("Misplaced infested floor");int floor=y/8;once(slots,"floor"+floor);
                int corner=floor%2==0?12:50;var turn=floor%2==0?rotation:rotation.getRotated(Rotation.CLOCKWISE_180);
                p.requireFrame(at(origin,rotation,corner,y,corner),turn,39,8,39);
            }else if(group.equals(POOL+"rooms"))p.requireFrame(p.origin(),p.rotation(),13,6,13);
            else {p.requireFrame(p.origin(),p.rotation(),1,2,1);encounters++;}
        }
        // 4 layouts + 32 shell pieces + 4 inner pieces + 12 floors; no synthesizing absent floors.
        if(slots.size()!=52)throw new IllegalArgumentException("Incomplete original infested temple shell or floors");
        var positions=new HashMap<BlockPos,List<Socket>>();
        for(var s:sockets){if(s.owner()<0||s.owner()>=pieces.size()||s.front()==null||s.top()==null)throw new IllegalArgumentException("Invalid infested connector");positions.computeIfAbsent(s.pos(),ignored->new ArrayList<>()).add(s);}
        var edges=new HashMap<Integer,Set<Integer>>();
        for(var out:sockets){int attached=0;for(var in:positions.getOrDefault(out.pos().relative(out.front()),List.of()))if(out.owner()!=in.owner()
                &&out.pool().equals(pool(key(pieces.get(in.owner()))))&&out.target().equals(in.name())&&out.front()==in.front().getOpposite()&&(!out.aligned()||out.top()==in.top())){
            attached++;edges.computeIfAbsent(out.owner(),ignored->new HashSet<>()).add(in.owner());edges.computeIfAbsent(in.owner(),ignored->new HashSet<>()).add(out.owner());
        }
            // Layout and corner pieces intentionally share horizontal sockets. Selected internal slots do not.
            if(out.front()==Direction.UP&&Set.of(POOL+"levels",POOL+"rooms",POOL+"spawners").contains(out.pool())&&attached!=1)
                throw new IllegalArgumentException("Missing or ambiguous original infested attachment");
        }
        var visited=new HashSet<Integer>();var queue=new ArrayDeque<Integer>();queue.add(0);while(!queue.isEmpty()){int i=queue.remove();if(visited.add(i))queue.addAll(edges.getOrDefault(i,Set.of()));}
        if(visited.size()!=pieces.size())throw new IllegalArgumentException("Detached infested temple content");
        var featureCells=new HashSet<Cell>();for(var f:features)if(!featureCells.add(f.pos()))throw new IllegalArgumentException("Original infested containers or devices overlap");
        if(features.stream().filter(f->f.block().equals("minecraft:spawner")).count()!=encounters)throw new IllegalArgumentException("Missing original infested spawner data");
        return new InfestedLayout(origin,rotation,List.copyOf(features));
    }
    Cell at(int x,int y,int z){var p=at(origin,rotation,x,y,z);return new Cell(p.getX(),p.getY(),p.getZ());}
    int groundY(){return origin.getY()+1;}
    @Override public InfestedLayout move(int x,int y,int z){return new InfestedLayout(origin.offset(x,y,z),rotation,features.stream().map(f->new Feature(f.pos().move(x,y,z),f.block(),f.data().copy())).toList());}
    @Override public BooleanSupplier survey(ServerLevel world,IslandPlan island,int exits){return new InfestedRoute(this,island,exits,new InfestedProbe(world))::step;}
}
