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

/** Validates the selected native graph; missing tower ends are never synthesized. */
record BanditTowersLayout(int groundY,List<Landing> landings,List<Feature> features) implements PerimeterLayout {
    static final String TARGET="dungeons_arise:bandit_towers", PREFIX=TARGET+"/bandit_towers_";
    static final String POOL="dungeons_arise:bandit/bandit_towers/bandit_towers_";
    record Socket(int owner,BlockPos pos,Direction front,Direction top,String name,String target,String pool,boolean aligned) {}
    record Feature(Cell pos,String block,String loot,CompoundTag spawnData,net.minecraft.nbt.ListTag spawnPotentials) {}
    record Landing(BlockPos origin,Rotation rotation) {
        Cell at(int x,int y,int z){var p=new BlockPos(x,y,z).rotate(rotation).offset(origin);return new Cell(p.getX(),p.getY(),p.getZ());}
    }
    static String key(WdaTemplatePiece p){if(!p.id().startsWith(PREFIX))throw new IllegalArgumentException("Foreign tower piece");return p.id().substring(PREFIX.length());}
    static boolean layer(String k){return k.matches("part_[0-4]|crossing_[0125]");}
    static boolean accepts(String pool,String k){
        if(!pool.startsWith(POOL))return false;
        return switch(pool.substring(POOL.length())) {
            case "main" -> k.matches("part_[0-4]|(?:base|roof)_terminator_0");
            case "crossing" -> k.matches("crossing_[0125]|crossing_terminator_0");
            case "bridges" -> k.matches("crossing_3_bridge|crossing_4_bridge_mini|crossing_terminator_0");
            case "rooms" -> k.matches("small_room_[0-3]|small_room_terminator_0");
            case "spawners" -> k.matches("skeleton_juggernaut_[0-2]|skeleton_assassin_[01]|ranged_illager_0|ranged_skeleton_1");
            default -> false;
        };
    }
    private static int[] size(String k){
        if(layer(k)||k.equals("crossing_3_bridge"))return new int[]{31,18,31};
        if(k.equals("crossing_4_bridge_mini"))return new int[]{9,5,5};
        if(k.equals("crossing_terminator_0"))return new int[]{5,5,5};
        if(k.equals("base_terminator_0"))return new int[]{31,7,31};
        if(k.equals("roof_terminator_0"))return new int[]{31,4,31};
        if(k.matches("small_room_[0-3]"))return new int[]{9,12,9};
        if(k.equals("small_room_terminator_0"))return new int[]{3,2,1};
        if(accepts(POOL+"spawners",k))return new int[]{1,2,1};
        throw new IllegalArgumentException("Unknown original tower piece "+k);
    }
    static BanditTowersLayout inspect(List<WdaTemplatePiece> pieces,WdaStructurePlanner.Environment env,List<Socket> sockets,List<Feature> features){
        if(env.boarding()||env.entryMode()!=DungeonSpec.EntryMode.PERIMETER||
                (env.surface()!=IslandPlan.Surface.GRASS&&env.surface()!=IslandPlan.Surface.ROCK))throw new IllegalArgumentException("Towers require a dry island");
        // JigsawPlacement applies the original single-element ground delta of one to absolute 135.
        if(pieces.isEmpty()||!key(pieces.get(0)).matches("crossing_[012]")||pieces.get(0).origin().getY()!=134)
            throw new IllegalArgumentException("Changed native tower root or height");
        var used=new HashSet<String>();var landings=new ArrayList<Landing>();int ground=Integer.MAX_VALUE;
        for(var p:pieces){String k=key(p);var s=size(k);
            if(p.terrainMatching()||!used.add(k+"@"+p.origin()))throw new IllegalArgumentException("Projected or duplicate tower piece");
            p.requireFrame(p.origin(),p.rotation(),s[0],s[1],s[2]);ground=Math.min(ground,p.bounds().minY());
            if(k.equals("crossing_terminator_0"))landings.add(new Landing(p.origin(),p.rotation()));
        }
        var byOwner=new HashMap<Integer,List<Socket>>();
        for(var s:sockets){if(s.owner()<0||s.owner()>=pieces.size()||s.front()==null||s.top()==null)throw new IllegalArgumentException("Invalid tower socket");
            byOwner.computeIfAbsent(s.owner(),ignored->new ArrayList<>()).add(s);}
        var edges=new HashMap<Integer,Set<Integer>>();
        for(var out:sockets){int matches=0;
            for(var in:sockets)if(out.owner()!=in.owner()&&(joins(out,in,pieces)||joins(in,out,pieces))){
                matches++;edges.computeIfAbsent(out.owner(),ignored->new HashSet<>()).add(in.owner());
            }
            String owner=key(pieces.get(out.owner()));
            boolean required=layer(owner)&&(out.front().getAxis().isVertical()&&out.pool().equals(POOL+"main")||out.pool().equals(POOL+"bridges"))
                    ||owner.matches("crossing_3_bridge|crossing_4_bridge_mini");
            if(matches>1||required&&matches!=1)throw new IllegalArgumentException("Open or ambiguous native tower shell at "+out.pos());
        }
        for(int i=0;i<pieces.size();i++)if(layer(key(pieces.get(i)))){
            var local=byOwner.getOrDefault(i,List.of());
            if(local.stream().filter(s->s.pool().equals(POOL+"main")&&s.front().getAxis().isVertical()).count()!=2)
                throw new IllegalArgumentException("Missing tower top or bottom connector");
        }
        var seen=new HashSet<Integer>();var queue=new ArrayDeque<Integer>();queue.add(0);
        while(!queue.isEmpty()){int i=queue.remove();if(seen.add(i))queue.addAll(edges.getOrDefault(i,Set.of()));}
        if(seen.size()!=pieces.size()||landings.isEmpty())throw new IllegalArgumentException("Detached tower content or no original bridge end");
        return new BanditTowersLayout(ground,List.copyOf(landings),List.copyOf(features));
    }
    private static boolean joins(Socket out,Socket in,List<WdaTemplatePiece> pieces){
        return accepts(out.pool(),key(pieces.get(in.owner())))&&out.target().equals(in.name())
                &&out.front()==in.front().getOpposite()&&out.pos().relative(out.front()).equals(in.pos())&&(!out.aligned()||out.top()==in.top());
    }
    @Override public BanditTowersLayout move(int x,int y,int z){return new BanditTowersLayout(Math.addExact(groundY,y),
            landings.stream().map(l->new Landing(l.origin().offset(x,y,z),l.rotation())).toList(),
            features.stream().map(f->new Feature(f.pos().move(x,y,z),f.block(),f.loot(),f.spawnData().copy(),f.spawnPotentials().copy())).toList());}
    @Override public BooleanSupplier survey(ServerLevel world,IslandPlan island,int exits){return new BanditTowersRoute(this,island,exits,new BanditTowersProbe(world))::step;}
}
