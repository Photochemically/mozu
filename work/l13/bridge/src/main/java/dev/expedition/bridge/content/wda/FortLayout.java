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

/** Complete native fort shell and connected optional contents. Never synthesizes missing pieces. */
record FortLayout(BlockPos origin, Rotation rotation, List<Feature> features, List<Spawn> inhabitants) implements PerimeterLayout {
    static final String TARGET="dungeons_arise:illager_fort";
    static final String PREFIX=TARGET+"/illager_fort_";
    static final String POOL="dungeons_arise:fortified/illager_fort/illager_fort_";
    static final int[][] OFFSETS={{0,0,0},{32,0,0},{32,0,27},{0,0,27},{0,43,0},{32,43,0},{0,43,27},{32,43,27}};
    static final int[][] SIZES={{32,43,27},{23,43,27},{23,43,20},{32,43,20},{32,17,27},{23,17,27},{32,17,20},{23,17,20}};
    record Socket(int owner,BlockPos pos,Direction front,Direction top,String name,String target,String pool,boolean aligned) {}
    record Feature(Cell pos,String block,String loot) {}
    record Spawn(BlockPos pos,String type) {}
    static String key(WdaTemplatePiece p) {
        if (!p.id().startsWith(PREFIX)) throw new IllegalArgumentException("Foreign fort piece");
        return p.id().substring(PREFIX.length());
    }
    static String pool(String key) {
        if (key.matches("part_[0-3]")) return POOL+"main";
        if (key.matches("part_[4-7]")) return POOL+"top";
        if (key.matches("room_[0-8]")) return POOL+"rooms";
        if (key.matches("(?:pillager|vindicator|evoker|illusioner)_0")) return POOL+"spawners";
        throw new IllegalArgumentException("Unknown fort piece: "+key);
    }
    static FortLayout inspect(List<WdaTemplatePiece> pieces,WdaStructurePlanner.Environment env,
                              List<Socket> sockets,List<Feature> features) {
        if (env.boarding() || env.entryMode()!=DungeonSpec.EntryMode.PERIMETER
                || (env.surface()!=IslandPlan.Surface.GRASS && env.surface()!=IslandPlan.Surface.ROCK))
            throw new IllegalArgumentException("Fort requires a dry perimeter");
        if (pieces.isEmpty() || !key(pieces.get(0)).matches("part_[0-3]")) throw new IllegalArgumentException("Unexpected fort root");
        var root=pieces.get(0);
        if (root.origin().getY()!=env.firstFreeY()-8) throw new IllegalArgumentException("Changed native fort height");
        var zero=pieces.stream().filter(p->p.id().equals(PREFIX+"part_0")).findFirst().orElseThrow(()->new IllegalArgumentException("Missing fort foundation"));
        var origin=zero.origin(); var rotation=zero.rotation();
        var shell=new BitSet(8); var used=new HashSet<String>(); var inhabitants=new ArrayList<Spawn>();
        for (var p:pieces) {
            String key=key(p); String pool=pool(key);
            if (p.terrainMatching() || !used.add(key+"@"+p.origin())) throw new IllegalArgumentException("Projected or duplicate fort piece");
            if (key.startsWith("part_")) {
                int i=Integer.parseInt(key.substring(5));
                if (shell.get(i)) throw new IllegalArgumentException("Duplicate fort shell quadrant");
                shell.set(i);var o=OFFSETS[i];var s=SIZES[i];
                p.requireFrame(new BlockPos(o[0],o[1],o[2]).rotate(rotation).offset(origin),rotation,s[0],s[1],s[2]);
            } else if (pool.equals(POOL+"rooms")) p.requireFrame(p.origin(),p.rotation(),9,9,14);
            else {
                p.requireFrame(p.origin(),p.rotation(),1,3,1);
                inhabitants.add(new Spawn(p.origin().above(),"minecraft:"+key.substring(0,key.lastIndexOf('_'))));
            }
        }
        if (shell.cardinality()!=8) throw new IllegalArgumentException("Incomplete original fort shell");
        var owned=new HashMap<Integer,List<Socket>>();
        for (var s:sockets) {
            if(s.owner()<0 || s.owner()>=pieces.size() || s.front()==null || s.top()==null)
                throw new IllegalArgumentException("Invalid fort socket owner or direction");
            owned.computeIfAbsent(s.owner(),ignored->new ArrayList<>()).add(s);
        }
        var seen=new HashSet<Integer>();seen.add(0);
        for(boolean changed=true;changed;) {
            changed=false;
            for(int child=1;child<pieces.size();child++) if(!seen.contains(child)) {
                boolean connected=false;
                for(int parent:List.copyOf(seen)) for(var out:owned.getOrDefault(parent,List.of()))
                    for(var in:owned.getOrDefault(child,List.of()))
                        if(out.pool().equals(pool(key(pieces.get(child)))) && out.target().equals(in.name())
                                && out.front()==in.front().getOpposite() && out.pos().relative(out.front()).equals(in.pos())
                                && (!out.aligned() || out.top()==in.top())) connected=true;
                if(connected){seen.add(child);changed=true;}
            }
        }
        if(seen.size()!=pieces.size())throw new IllegalArgumentException("Detached or misoriented fort content");
        return new FortLayout(origin,rotation,List.copyOf(features),List.copyOf(inhabitants));
    }
    Cell at(int x,int y,int z) {var p=new BlockPos(x,y,z).rotate(rotation).offset(origin);return new Cell(p.getX(),p.getY(),p.getZ());}
    Cell side(int side,int x,int y,int z) {return at(side==0?x:54-x,y,z);}
    int groundY(){return origin.getY()+8;}
    boolean matchesEntities(List<Spawn> actual){return actual.size()==inhabitants.size() && new HashSet<>(actual).equals(new HashSet<>(inhabitants));}
    @Override public FortLayout move(int x,int y,int z){return new FortLayout(origin.offset(x,y,z),rotation,
            features.stream().map(f->new Feature(f.pos().move(x,y,z),f.block(),f.loot())).toList(),
            inhabitants.stream().map(s->new Spawn(s.pos().offset(x,y,z),s.type())).toList());}
    @Override public BooleanSupplier survey(ServerLevel world,IslandPlan island,int exits){return new FortRoute(this,island,exits,new FortProbe(world))::step;}
}
