package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.DungeonSpec;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Original four-quadrant shell and its complete native encounter attachments. */
record ColiseumLayout(BlockPos origin,Rotation rotation,List<Spawn> inhabitants,Device spawner) implements PerimeterLayout {
    static final String TARGET="dungeons_arise:coliseum", PREFIX=TARGET+"/coliseum_";
    static final int[][] OFFSETS={{0,0,0},{32,0,0},{32,0,32},{0,0,32}};
    // Attachment origins, one cell above each original armored_iron socket.
    static final int[][] ENEMIES={{8,3,19},{7,8,25},{16,8,15},{27,8,6},
            {44,3,11},{47,8,15},{38,3,57},{37,8,56},{54,8,39},{17,3,51},{15,8,46}};
    record Spawn(BlockPos pos,String type) {}
    record Device(Cell pos,CompoundTag spawnData,ListTag potentials) {}
    private static BlockPos at(BlockPos origin,Rotation rotation,int[] p){return new BlockPos(p[0],p[1],p[2]).rotate(rotation).offset(origin);}
    static ColiseumLayout inspect(List<WdaTemplatePiece> pieces,WdaStructurePlanner.Environment env,List<Device> devices){
        if(env.boarding()||env.entryMode()!=DungeonSpec.EntryMode.PERIMETER||
                (env.surface()!=IslandPlan.Surface.GRASS&&env.surface()!=IslandPlan.Surface.ROCK))throw new IllegalArgumentException("Coliseum requires a dry island");
        if(pieces.isEmpty()||!pieces.get(0).id().matches(PREFIX+"part_[0-3]")||pieces.get(0).origin().getY()!=env.firstFreeY()-4)
            throw new IllegalArgumentException("Changed original coliseum root or height");
        var zero=pieces.stream().filter(p->p.id().equals(PREFIX+"part_0")).findFirst().orElseThrow(()->new IllegalArgumentException("Missing coliseum quadrant"));
        var origin=zero.origin();var rotation=zero.rotation();var shell=new BitSet(4);var enemies=new HashSet<BlockPos>();
        var expected=new HashSet<BlockPos>();for(var e:ENEMIES)expected.add(at(origin,rotation,e));
        int deviceCount=0;
        for(var p:pieces){
            if(p.terrainMatching()||!p.id().startsWith(PREFIX))throw new IllegalArgumentException("Unexpected coliseum piece");
            String key=p.id().substring(PREFIX.length());
            if(key.matches("part_[0-3]")){
                int i=Integer.parseInt(key.substring(5));if(shell.get(i))throw new IllegalArgumentException("Duplicate coliseum quadrant");shell.set(i);
                p.requireFrame(at(origin,rotation,OFFSETS[i]),rotation,32,32,32);
            }else if(key.equals("enemy_0")){
                if(!expected.contains(p.origin())||!enemies.add(p.origin()))throw new IllegalArgumentException("Detached or duplicate coliseum enemy");
                p.requireFrame(p.origin(),p.rotation(),1,3,1); // Original rollable socket permits each enemy rotation.
            }else if(key.equals("spawner_0")){
                deviceCount++;p.requireFrame(at(origin,rotation,new int[]{31,3,31}),rotation.getRotated(Rotation.CLOCKWISE_90),1,2,1);
            }else throw new IllegalArgumentException("Unknown coliseum template "+key);
        }
        if(shell.cardinality()!=4||!enemies.equals(expected)||deviceCount!=1||devices.size()!=1)
            throw new IllegalArgumentException("Incomplete original coliseum shell or encounters");
        var device=devices.get(0);var location=at(origin,rotation,new int[]{31,3,31});
        if(!device.pos().equals(new Cell(location.getX(),location.getY(),location.getZ()))
                ||!device.spawnData().getCompound("entity").getString("id").equals("minecraft:phantom"))
            throw new IllegalArgumentException("Changed original coliseum spawner");
        return new ColiseumLayout(origin,rotation,enemies.stream().sorted(Comparator.comparingLong(BlockPos::asLong))
                .map(p->new Spawn(p.above(),"minecraft:skeleton")).toList(),device);
    }
    Cell at(int x,int y,int z){var p=at(origin,rotation,new int[]{x,y,z});return new Cell(p.getX(),p.getY(),p.getZ());}
    Cell arch(int side,int along){return switch(side){case 0->at(along,2,31);case 1->at(63-along,2,31);case 2->at(31,2,along);default->at(31,2,63-along);};}
    int groundY(){return origin.getY()+2;}
    boolean matchesEntities(List<Spawn> actual){return actual.size()==inhabitants.size()&&new HashSet<>(actual).equals(new HashSet<>(inhabitants));}
    @Override public ColiseumLayout move(int x,int y,int z){return new ColiseumLayout(origin.offset(x,y,z),rotation,
            inhabitants.stream().map(s->new Spawn(s.pos().offset(x,y,z),s.type())).toList(),
            new Device(spawner.pos().move(x,y,z),spawner.spawnData().copy(),spawner.potentials().copy()));}
    @Override public BooleanSupplier survey(ServerLevel world,IslandPlan island,int exits){return new ColiseumRoute(this,island,exits,new ColiseumProbe(world))::step;}
}
