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

/** Audited native connector geometry; no replacement buildings or changes to pool selection. */
record BanditLayout(int groundY,Cell center,List<Plot> plots,List<Feature> features,List<Spawn> inhabitants) implements PerimeterLayout {
    static final String TARGET="dungeons_arise:bandit_village";
    static final String PREFIX=TARGET+"/bandit_village_";
    static final String POOL="dungeons_arise:bandit/bandit_village/bandit_village_";
    record Joint(BlockPos pos,Direction front,Direction top,String name,String target,String pool,boolean aligned) {}
    record Shape(BlockPos size,List<Joint> joints) {}
    static final Map<String,Shape> SHAPES=shapes();
    private static Joint joint(int x,int y,int z,String front,String top,String name,String target,String pool,boolean aligned) {
        return new Joint(new BlockPos(x,y,z),Direction.byName(front),Direction.byName(top),name,target,pool,aligned);
    }
    private static Map<String,Shape> shapes() {
        var shapes=new HashMap<String,Shape>();
        shapes.put("archer_0",new Shape(new BlockPos(1,3,1),List.of(joint(0,0,0,"down","north","dungeons_arise:archer_spawners","dungeons_arise:archer_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true))));
        shapes.put("assassin_0",new Shape(new BlockPos(1,3,1),List.of(joint(0,0,0,"down","north","dungeons_arise:assassin_spawners","dungeons_arise:assassin_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true))));
        shapes.put("deco_0",new Shape(new BlockPos(7,10,7),List.of(joint(0,0,3,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false))));
        shapes.put("deco_1",new Shape(new BlockPos(7,7,7),List.of(joint(0,0,3,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false))));
        shapes.put("deco_10",new Shape(new BlockPos(7,10,7),List.of(joint(0,1,3,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(4,2,3,"up","south","dungeons_arise:assassin_spawners","dungeons_arise:assassin_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true))));
        shapes.put("deco_11",new Shape(new BlockPos(7,10,7),List.of(joint(0,1,3,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(3,1,4,"up","north","dungeons_arise:hoglin_spawners","dungeons_arise:hoglin_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true))));
        shapes.put("deco_12",new Shape(new BlockPos(7,10,7),List.of(joint(0,1,3,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(2,2,3,"up","east","dungeons_arise:assassin_spawners","dungeons_arise:assassin_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true))));
        shapes.put("deco_2",new Shape(new BlockPos(7,10,7),List.of(joint(0,0,3,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(3,3,3,"up","north","dungeons_arise:hoglin_spawners","dungeons_arise:hoglin_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true))));
        shapes.put("deco_4",new Shape(new BlockPos(7,18,7),List.of(joint(0,0,4,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false))));
        shapes.put("deco_5",new Shape(new BlockPos(7,18,7),List.of(joint(0,0,3,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(2,1,3,"up","south","dungeons_arise:assassin_spawners","dungeons_arise:assassin_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true))));
        shapes.put("deco_6",new Shape(new BlockPos(7,10,7),List.of(joint(0,1,3,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(1,2,4,"up","west","dungeons_arise:hoglin_spawners","dungeons_arise:hoglin_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true))));
        shapes.put("deco_7",new Shape(new BlockPos(7,10,7),List.of(joint(0,1,3,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false))));
        shapes.put("deco_8",new Shape(new BlockPos(7,10,7),List.of(joint(0,1,3,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(3,2,0,"up","west","dungeons_arise:hoglin_spawners","dungeons_arise:hoglin_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true))));
        shapes.put("deco_9",new Shape(new BlockPos(7,10,7),List.of(joint(0,1,3,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false))));
        shapes.put("hoglin_0",new Shape(new BlockPos(1,2,1),List.of(joint(0,1,0,"down","north","dungeons_arise:hoglin_spawners","dungeons_arise:hoglin_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true))));
        shapes.put("hoglin_1",new Shape(new BlockPos(1,2,1),List.of(joint(0,1,0,"down","north","dungeons_arise:hoglin_spawners","dungeons_arise:hoglin_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true))));
        shapes.put("house_0",new Shape(new BlockPos(7,12,7),List.of(joint(3,1,0,"north","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(1,10,4,"up","south","dungeons_arise:archer_spawners","dungeons_arise:archer_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true),joint(5,10,5,"up","west","dungeons_arise:archer_spawners","dungeons_arise:archer_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true))));
        shapes.put("house_1",new Shape(new BlockPos(7,30,7),List.of(joint(0,0,3,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false))));
        shapes.put("house_2",new Shape(new BlockPos(7,15,7),List.of(joint(3,1,0,"north","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(3,1,3,"up","west","dungeons_arise:assassin_spawners","dungeons_arise:assassin_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true))));
        shapes.put("house_3",new Shape(new BlockPos(7,15,7),List.of(joint(0,1,3,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(1,10,1,"up","north","dungeons_arise:archer_spawners","dungeons_arise:archer_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true),joint(1,10,5,"up","east","dungeons_arise:archer_spawners","dungeons_arise:archer_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true),joint(5,10,1,"up","east","dungeons_arise:archer_spawners","dungeons_arise:archer_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true),joint(5,10,5,"up","west","dungeons_arise:archer_spawners","dungeons_arise:archer_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true))));
        shapes.put("house_4",new Shape(new BlockPos(7,15,7),List.of(joint(6,0,3,"east","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false))));
        shapes.put("house_5",new Shape(new BlockPos(7,15,7),List.of(joint(3,1,0,"north","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(4,2,2,"up","west","dungeons_arise:assassin_spawners","dungeons_arise:assassin_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true),joint(1,9,1,"up","east","dungeons_arise:archer_spawners","dungeons_arise:archer_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true))));
        shapes.put("house_6",new Shape(new BlockPos(7,20,7),List.of(joint(6,1,3,"east","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(1,16,2,"up","east","dungeons_arise:archer_spawners","dungeons_arise:archer_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true),joint(5,16,4,"up","west","dungeons_arise:archer_spawners","dungeons_arise:archer_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true))));
        shapes.put("house_7",new Shape(new BlockPos(7,15,7),List.of(joint(3,1,0,"north","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(5,2,4,"up","east","dungeons_arise:assassin_spawners","dungeons_arise:assassin_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true))));
        shapes.put("house_8",new Shape(new BlockPos(7,15,7),List.of(joint(0,1,3,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(1,10,5,"up","west","dungeons_arise:archer_spawners","dungeons_arise:archer_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true),joint(5,10,1,"up","west","dungeons_arise:archer_spawners","dungeons_arise:archer_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true))));
        shapes.put("house_9",new Shape(new BlockPos(7,30,7),List.of(joint(3,1,0,"north","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(3,2,3,"up","east","dungeons_arise:assassin_spawners","dungeons_arise:assassin_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true),joint(3,19,3,"up","west","dungeons_arise:archer_spawners","dungeons_arise:archer_spawners","dungeons_arise:bandit/bandit_village/bandit_village_spawners",true))));
        shapes.put("street_0",new Shape(new BlockPos(3,1,11),List.of(joint(0,0,3,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(0,0,4,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(0,0,5,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(0,0,6,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(0,0,7,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(1,0,0,"north","up","dungeons_arise:streets","dungeons_arise:streets_crossing","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(1,0,10,"south","up","dungeons_arise:streets","dungeons_arise:streets_crossing","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(2,0,3,"east","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(2,0,4,"east","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(2,0,5,"east","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(2,0,6,"east","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(2,0,7,"east","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false))));
        shapes.put("street_2",new Shape(new BlockPos(3,1,3),List.of(joint(0,0,1,"west","up","dungeons_arise:streets_crossing","dungeons_arise:streets","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(1,0,0,"north","up","dungeons_arise:streets_crossing","dungeons_arise:streets","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(1,0,2,"south","up","dungeons_arise:streets_crossing","dungeons_arise:streets","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(2,0,1,"east","up","dungeons_arise:streets_crossing","dungeons_arise:streets","dungeons_arise:bandit/bandit_village/bandit_village_streets",false))));
        shapes.put("street_3",new Shape(new BlockPos(3,1,3),List.of(joint(1,0,0,"north","up","dungeons_arise:streets_crossing","dungeons_arise:streets","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(1,0,2,"south","up","dungeons_arise:streets_crossing","dungeons_arise:streets","dungeons_arise:bandit/bandit_village/bandit_village_streets",false))));
        shapes.put("street_4",new Shape(new BlockPos(7,1,11),List.of(joint(0,0,4,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(0,0,5,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(0,0,6,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(0,0,7,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(1,0,10,"south","up","dungeons_arise:streets","dungeons_arise:streets_crossing","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(4,0,0,"north","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(5,0,0,"north","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(6,0,0,"north","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(6,0,1,"east","up","dungeons_arise:streets","dungeons_arise:streets_crossing","dungeons_arise:bandit/bandit_village/bandit_village_streets",false))));
        shapes.put("street_5",new Shape(new BlockPos(7,1,11),List.of(joint(0,0,0,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(0,0,1,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(0,0,2,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(0,0,3,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(1,0,0,"north","up","dungeons_arise:streets","dungeons_arise:streets_crossing","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(5,0,10,"south","up","dungeons_arise:streets","dungeons_arise:streets_crossing","dungeons_arise:bandit/bandit_village/bandit_village_streets",false),joint(6,0,8,"east","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(6,0,9,"east","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false),joint(6,0,10,"east","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_houses",false))));
        shapes.put("deco_3",new Shape(new BlockPos(7,10,7),List.of(joint(0,0,3,"west","up","dungeons_arise:streets_entrance","dungeons_arise:streets_entrance","dungeons_arise:bandit/bandit_village/bandit_village_streets",false))));
        return Map.copyOf(shapes);
    }
    record Plot(String id,BlockPos origin,Rotation rotation) {
        Cell at(int x,int y,int z) {return point(new BlockPos(x,y,z).rotate(rotation).offset(origin));}
        Joint inlet() {return SHAPES.get(id).joints().stream().filter(j->j.name().equals("dungeons_arise:streets_entrance")).findFirst().orElseThrow();}
        Cell approach() {
            if (id.equals("house_1")) return at(-2,0,3);
            if (id.equals("house_4")) return at(5,0,-2);
            if (id.equals("house_6")) return at(7,1,4);
            var j=inlet();var p=j.pos().relative(j.front());return at(p.getX(),p.getY(),p.getZ());
        }
        Cell stepBlock() {return id.equals("house_1")?at(-1,0,3):id.equals("house_4")?at(5,0,-1):null;}
    }
    record Feature(Cell pos,String block,String loot,boolean source) {}
    record Spawn(BlockPos position,String type) {}
    static Cell point(BlockPos p) {return new Cell(p.getX(),p.getY(),p.getZ());}
    private static String id(WdaTemplatePiece p) {
        if (!p.id().startsWith(PREFIX)) throw new IllegalArgumentException("Foreign bandit piece");
        return p.id().substring(PREFIX.length());
    }
    private static String pool(String id) {return POOL+(id.equals("street_2")?"start":id.startsWith("street_")?"streets":
            id.startsWith("house_")||id.startsWith("deco_")?"houses":"spawners");}
    private static BlockPos jointPos(WdaTemplatePiece p,Joint j,int ground) {
        var pos=j.pos().rotate(p.rotation()).offset(p.origin());
        return p.terrainMatching()?new BlockPos(pos.getX(),ground,pos.getZ()):pos;
    }
    private static boolean attached(WdaTemplatePiece parent,WdaTemplatePiece child,int ground) {
        for (var a:SHAPES.get(id(parent)).joints()) {
            if (!a.pool().equals(pool(id(child)))) continue;
            var front=parent.rotation().rotate(a.front());
            var expected=jointPos(parent,a,ground).relative(front);
            // Native terrain-matching connections use surface height, independently of raw piece Y.
            if (parent.terrainMatching()||child.terrainMatching()) expected=new BlockPos(expected.getX(),ground,expected.getZ());
            for (var b:SHAPES.get(id(child)).joints()) if (a.target().equals(b.name())
                    && child.rotation().rotate(b.front())==front.getOpposite()
                    && (!a.aligned()||parent.rotation().rotate(a.top())==child.rotation().rotate(b.top()))
                    && expected.equals(jointPos(child,b,ground))) return true;
        }
        return false;
    }
    static BanditLayout inspect(List<WdaTemplatePiece> pieces,WdaStructurePlanner.Environment env,List<Feature> features) {
        if (env.boarding() || env.entryMode()!=DungeonSpec.EntryMode.PERIMETER
                || (env.surface()!=IslandPlan.Surface.GRASS && env.surface()!=IslandPlan.Surface.ROCK))
            throw new IllegalArgumentException("Bandit village requires a dry perimeter");
        if (pieces.isEmpty()||!pieces.get(0).id().equals(PREFIX+"street_2")) throw new IllegalArgumentException("Missing bandit starting street");
        var plots=new ArrayList<Plot>();var inhabitants=new ArrayList<Spawn>();
        var occupied=new HashSet<BlockPos>();
        for (int i=0;i<pieces.size();i++) {
            var p=pieces.get(i);var key=id(p);var shape=SHAPES.get(key);
            if (shape==null || (i>0&&key.equals("street_2")) || !occupied.add(p.origin()))
                throw new IllegalArgumentException("Unknown or duplicate bandit piece");
            var size=shape.size();p.requireFrame(p.origin(),p.rotation(),size.getX(),size.getY(),size.getZ());
            boolean road=key.startsWith("street_");
            if (road!=p.terrainMatching() || (road&&p.origin().getY()!=env.firstFreeY()-(i==0?1:0)))
                throw new IllegalArgumentException("Changed bandit road projection or grade");
            if (key.startsWith("house_")||key.startsWith("deco_")) {
                var plot=new Plot(key,p.origin(),p.rotation());
                if(p.origin().getY()+plot.inlet().pos().getY()!=env.firstFreeY())
                    throw new IllegalArgumentException("Bandit plot is off grade");
                plots.add(plot);
            }
            if (key.equals("archer_0")||key.equals("assassin_0"))
                inhabitants.add(new Spawn(p.origin().above(),key.equals("archer_0")?"minecraft:skeleton":"minecraft:husk"));
            // Native non-encounter footprints cannot overlap; encounter children are deliberately inside plots.
            if (!pool(key).equals(POOL+"spawners")) for (int j=0;j<i;j++) {
                var other=pieces.get(j);if(pool(id(other)).equals(POOL+"spawners"))continue;
                var a=p.bounds();var b=other.bounds();
                if(a.minX()<=b.maxX()&&a.maxX()>=b.minX()&&a.minZ()<=b.maxZ()&&a.maxZ()>=b.minZ())
                    throw new IllegalArgumentException("Overlapping bandit plots or roads");
            }
        }
        if (plots.isEmpty()) throw new IllegalArgumentException("Bandit village has no generated plots");
        var seen=new HashSet<Integer>();seen.add(0);
        for (boolean changed=true;changed;) {
            changed=false;
            for(int i=1;i<pieces.size();i++) if(!seen.contains(i)) {
                for(int j:List.copyOf(seen)) if(attached(pieces.get(j),pieces.get(i),env.firstFreeY())) {seen.add(i);changed=true;break;}
            }
        }
        if(seen.size()!=pieces.size())throw new IllegalArgumentException("Detached or misoriented bandit attachment");
        var root=pieces.get(0);var center=new BlockPos(1,0,1).rotate(root.rotation()).offset(root.origin());
        return new BanditLayout(env.firstFreeY(),new Cell(center.getX(),env.firstFreeY(),center.getZ()),
                List.copyOf(plots),List.copyOf(features),List.copyOf(inhabitants));
    }
    boolean matchesEntities(List<Spawn> actual) {return actual.size()==inhabitants.size()&&new HashSet<>(actual).equals(new HashSet<>(inhabitants));}
    @Override public BanditLayout move(int x,int y,int z) {
        return new BanditLayout(groundY+y,center.move(x,y,z),plots.stream().map(p->new Plot(p.id(),p.origin().offset(x,y,z),p.rotation())).toList(),
                features.stream().map(f->new Feature(f.pos().move(x,y,z),f.block(),f.loot(),f.source())).toList(),
                inhabitants.stream().map(s->new Spawn(s.position().offset(x,y,z),s.type())).toList());
    }
    @Override public BooleanSupplier survey(ServerLevel world,IslandPlan island,int exits) {
        return new BanditRoute(this,island,exits,new BanditProbe(world))::step;
    }
}
