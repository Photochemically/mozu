package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Original underground graph. Native optional branches stay optional; selected pieces must remain connected. */
record PlagueLayout(List<InteriorRoom> rooms,List<Feature> features) {
    static final String TARGET="dungeons_arise:plague_asylum", PREFIX=TARGET+"/plague_asylum_";
    static final String POOL="dungeons_arise:haunted/plague_asylum/plague_asylum_";
    static final String ROOT="corridor_4_crossing_pit";
    record Socket(int owner,BlockPos pos,Direction front,Direction top,String name,String target,String pool,boolean aligned,boolean air) {}
    record Feature(int owner,Cell pos,String block,CompoundTag data) {}
    static void requireDenseNormalCell(CompoundTag template) {
        var palette=template.getList("palette",10);var blocks=template.getList("blocks",10);var seen=new HashSet<Cell>();
        for(var value:blocks) {
            var b=(CompoundTag)value;var p=b.getList("pos",3);var c=new Cell(p.getInt(0),p.getInt(1),p.getInt(2));
            String id=palette.getCompound(b.getInt("state")).getString("Name");
            if(c.x()<0||c.x()>=8||c.y()<0||c.y()>=7||c.z()<0||c.z()>=9||!seen.add(c)||id.equals("minecraft:structure_void")
                    ||id.equals("minecraft:jigsaw")&&b.getCompound("nbt").getString("final_state").equals("minecraft:structure_void"))
                throw new IllegalArgumentException("Changed normal-cell overwrite footprint");
        }
        if(seen.size()!=504)throw new IllegalArgumentException("Sparse normal cell cannot replace original placeholder");
    }
    static void validateEnvironment(WdaStructurePlanner.Environment env) {
        if(env.entryMode()!=DungeonSpec.EntryMode.START_ROOM || env.surface()!=IslandPlan.Surface.ROCK || env.boarding())
            throw new IllegalArgumentException("Plague asylum requires rock/start_room; no surface entrance or tunnel substitution");
    }
    static String key(WdaTemplatePiece p) {
        if(!p.id().startsWith(PREFIX))throw new IllegalArgumentException("Foreign plague asylum component");
        return p.id().substring(PREFIX.length());
    }
    static int[] size(String k) {
        if(k.matches("cell_normal_[0-6]"))return new int[]{8,7,9};
        if(k.matches("cell_security_[0-4]")||k.matches("passage_[0-2]"))return new int[]{7,5,7};
        if(k.matches("evoker_fangs_0|skeleton_[01]|skeleton_2_wither|witch_0|zombie_horde_0"))return new int[]{1,2,1};
        return switch(k) {
            case "corridor_0" -> new int[]{11,9,17};
            case "corridor_1_cells","room_0","room_5_brews" -> new int[]{17,9,17};
            case "corridor_2_crossing" -> new int[]{17,9,14};
            case "corridor_3_stairway" -> new int[]{11,18,17};
            case ROOT,"room_1_double","room_2_double" -> new int[]{17,18,17};
            case "room_3_cells" -> new int[]{19,9,17};
            case "room_4_pit" -> new int[]{34,18,17};
            case "room_6_water" -> new int[]{23,9,23};
            default -> throw new IllegalArgumentException("Unknown plague asylum component "+k);
        };
    }
    static boolean belongs(String k,String pool) {
        if(pool.equals(POOL+"start"))return k.equals(ROOT);
        if(pool.equals(POOL+"corridors"))return k.startsWith("corridor_");
        if(pool.equals(POOL+"rooms"))return k.startsWith("room_");
        if(pool.equals(POOL+"deco"))return k.startsWith("cell_");
        if(pool.equals(POOL+"spawners"))return size(k)[0]==1;
        return pool.equals(POOL+"passages") && (k.startsWith("passage_") || Set.of(ROOT,"room_2_double","room_3_cells").contains(k));
    }
    static CompoundTag stableData(String block,CompoundTag n) {
        var result=new CompoundTag();
        if(block.equals("minecraft:spawner")) {
            result.put("SpawnData",n.getCompound("SpawnData").copy());result.put("SpawnPotentials",n.getList("SpawnPotentials",10).copy());
            for(var f:List.of("MinSpawnDelay","MaxSpawnDelay","SpawnCount","MaxNearbyEntities","RequiredPlayerRange","SpawnRange"))result.putInt(f,n.getInt(f));
        } else if(block.equals("minecraft:water")) result.putInt("level",n.getInt("level"));
        else if(n.contains("LootTable")) result.putString("LootTable",n.getString("LootTable"));
        else result.put("Items",n.getList("Items",10).copy());
        // Native container seeds and live spawner Delay are not immutable template settings.
        return result;
    }
    static PlagueLayout inspect(List<WdaTemplatePiece> pieces,WdaStructurePlanner.Environment env,
                               BlockBounds complete,List<Socket> sockets,List<Feature> features) {
        validateEnvironment(env);
        if(pieces.isEmpty() || !key(pieces.get(0)).equals(ROOT) || pieces.get(0).origin().getY()!=-1)
            throw new IllegalArgumentException("Changed plague asylum absolute native start");
        var unique=new HashSet<String>();int spawners=0;
        for(var p:pieces) {
            String k=key(p);var s=size(k);
            if(p.terrainMatching() || !unique.add(k+"@"+p.origin()))throw new IllegalArgumentException("Projected or duplicate plague asylum component");
            p.requireFrame(p.origin(),p.rotation(),s[0],s[1],s[2]);
            if(s[0]==1)spawners++;
        }
        var raw=pieces.stream().map(WdaTemplatePiece::bounds).reduce(IslandPlan::union).orElseThrow();
        if(!raw.expand(12).equals(complete))throw new IllegalArgumentException("Original bury reservation changed");
        var positions=new HashMap<BlockPos,List<Socket>>();
        for(var s:sockets) {
            if(s.owner()<0 || s.owner()>=pieces.size() || s.front()==null || s.top()==null
                    || !pieces.get(s.owner()).bounds().contains(s.pos().getX(),s.pos().getY(),s.pos().getZ()))
                throw new IllegalArgumentException("Invalid plague asylum connector");
            positions.computeIfAbsent(s.pos(),ignored->new ArrayList<>()).add(s);
        }
        var edges=new HashMap<Integer,Set<Integer>>();
        for(var out:sockets)for(var in:positions.getOrDefault(out.pos().relative(out.front()),List.of())) {
            if(out.owner()==in.owner() || !belongs(key(pieces.get(in.owner())),out.pool()) || !out.target().equals(in.name())
                    || out.front()!=in.front().getOpposite() || out.aligned()&&out.top()!=in.top())continue;
            edges.computeIfAbsent(out.owner(),ignored->new HashSet<>()).add(in.owner());
            edges.computeIfAbsent(in.owner(),ignored->new HashSet<>()).add(out.owner());
        }
        var seen=new HashSet<Integer>();var queue=new ArrayDeque<Integer>();queue.add(0);
        while(!queue.isEmpty()){int i=queue.remove();if(seen.add(i))queue.addAll(edges.getOrDefault(i,Set.of()));}
        if(seen.size()!=pieces.size())throw new IllegalArgumentException("Disconnected original plague asylum component");
        var rooms=new ArrayList<InteriorRoom>();
        for(int i=0;i<pieces.size();i++) {
            var passages=new LinkedHashSet<Cell>();
            for(var s:sockets)if(s.owner()==i&&s.air()&&s.front().getAxis().isHorizontal())passages.add(new Cell(s.pos().getX(),s.pos().getY(),s.pos().getZ()));
            if(i==0&&passages.isEmpty())throw new IllegalArgumentException("Original start passages missing");
            if(!passages.isEmpty())rooms.add(new InteriorRoom(pieces.get(i).bounds(),List.copyOf(passages)));
        }
        var resolved=new ArrayList<Feature>();var cells=new HashSet<Cell>();
        for(var f:features) {
            if(f.owner()<0||f.owner()>=pieces.size())throw new IllegalArgumentException("Invalid plague feature owner");
            boolean replaced=false;
            for(int i=f.owner()+1;i<pieces.size();i++) {
                var child=pieces.get(i);if(!key(child).startsWith("cell_normal_")||!child.bounds().contains(f.pos().x(),f.pos().y(),f.pos().z()))continue;
                var parent=pieces.get(f.owner());
                // The original corridor already contains two placeholder cells. A chosen dense normal cell
                // replaces exactly its placeholder chest, possibly with air or furniture. Preserve native order.
                var pos=new BlockPos(f.pos().x(),f.pos().y(),f.pos().z());
                boolean placeholder=List.of(new BlockPos(2,3,1),new BlockPos(2,3,9)).stream()
                        .anyMatch(p->p.rotate(parent.rotation()).offset(parent.origin()).equals(pos));
                if(!key(parent).equals("corridor_1_cells")||!f.block().equals("minecraft:chest")||!placeholder
                        ||!parent.bounds().contains(child.bounds())||!edges.getOrDefault(f.owner(),Set.of()).contains(i))
                    throw new IllegalArgumentException("Unexpected normal-cell content overwrite");
                replaced=true;
            }
            if(!replaced) {
                if(!cells.add(f.pos())||!raw.contains(f.pos().x(),f.pos().y(),f.pos().z()))throw new IllegalArgumentException("Overlapping or misplaced plague asylum device");
                resolved.add(f);
            }
        }
        if(resolved.stream().filter(f->f.block().equals("minecraft:spawner")).count()!=spawners)throw new IllegalArgumentException("Missing original plague asylum spawner data");
        return new PlagueLayout(List.copyOf(rooms),List.copyOf(resolved));
    }
    PlagueLayout move(int x,int y,int z) {
        return new PlagueLayout(rooms.stream().map(r->r.move(x,y,z)).toList(),
                features.stream().map(f->new Feature(f.owner(),f.pos().move(x,y,z),f.block(),f.data().copy())).toList());
    }
}
