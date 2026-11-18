package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.DungeonSpec;
import dev.expedition.bridge.domain.IslandPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Complete original fossil, including its intentionally buried treasure. No excavation or replacement. */
record HindLayout(BlockPos origin,Rotation rotation,List<Feature> features) implements PerimeterLayout {
    static final String TARGET="dungeons_arise:ceryneian_hind", PREFIX=TARGET+"/ceryneian_hind_part_";
    record Feature(Cell pos,String block,String axis,String facing,String loot) {}
    static HindLayout inspect(List<WdaTemplatePiece> pieces,WdaStructurePlanner.Environment env,List<Feature> features){
        if(env.boarding()||env.entryMode()!=DungeonSpec.EntryMode.PERIMETER
                ||(env.surface()!=IslandPlan.Surface.GRASS&&env.surface()!=IslandPlan.Surface.ROCK))
            throw new IllegalArgumentException("Ceryneian hind requires a dry perimeter island");
        if(pieces.size()!=3||!pieces.get(0).id().equals(PREFIX+1)||pieces.get(0).origin().getY()!=env.firstFreeY()-17)
            throw new IllegalArgumentException("Incomplete fossil or changed original buried root");
        var zero=pieces.stream().filter(p->p.id().equals(PREFIX+0)).findFirst().orElseThrow(()->new IllegalArgumentException("Missing fossil head"));
        var origin=zero.origin();var rotation=zero.rotation();var seen=new BitSet(3);
        for(var p:pieces){
            if(p.terrainMatching()||!p.id().matches(PREFIX+"[0-2]"))throw new IllegalArgumentException("Foreign or projected fossil piece");
            int i=Integer.parseInt(p.id().substring(PREFIX.length()));if(seen.get(i))throw new IllegalArgumentException("Duplicate fossil piece");seen.set(i);
            p.requireFrame(new BlockPos(48*i,0,0).rotate(rotation).offset(origin),rotation,48,48,48);
        }
        if(seen.cardinality()!=3)throw new IllegalArgumentException("Missing fossil section");
        var counts=new HashMap<String,Integer>();var cells=new HashSet<Cell>();
        for(var f:features){
            if(!cells.add(f.pos())||pieces.stream().noneMatch(p->p.bounds().contains(f.pos().x(),f.pos().y(),f.pos().z())))
                throw new IllegalArgumentException("Duplicate or detached fossil content");
            counts.merge(f.block(),1,Integer::sum);
        }
        if(!counts.equals(Map.of("minecraft:smooth_quartz",2259,"minecraft:bone_block",2260,"minecraft:gold_block",2,"minecraft:air",1,"minecraft:chest",1)))
            throw new IllegalArgumentException("Changed original fossil content inventory");
        return new HindLayout(origin,rotation,List.copyOf(features));
    }
    Cell at(int x,int y,int z){var p=new BlockPos(x,y,z).rotate(rotation).offset(origin);return new Cell(p.getX(),p.getY(),p.getZ());}
    int groundY(){return origin.getY()+17;}
    List<Cell> approaches(){return List.of(at(30,17,10),at(72,17,23),at(126,17,23));}
    @Override public HindLayout move(int x,int y,int z){return new HindLayout(origin.offset(x,y,z),rotation,
            features.stream().map(f->new Feature(f.pos().move(x,y,z),f.block(),f.axis(),f.facing(),f.loot())).toList());}
    @Override public BooleanSupplier survey(ServerLevel world,IslandPlan island,int exits){return new HindRoute(this,island,exits,new HindProbe(world))::step;}
}
