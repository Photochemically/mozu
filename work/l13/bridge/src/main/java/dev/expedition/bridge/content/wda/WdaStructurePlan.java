package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import java.util.*;

/** Owns a complete native piece graph. Relocation preserves the original elements, processors and junctions. */
public final class WdaStructurePlan {
    private final ResourceLocation id;
    private StructureStart start;
    private IslandPlan island;
    private final boolean boarding;
    private final DungeonSpec.EntryMode entryMode;
    private List<InteriorRoom> interiorRooms;
    private MechanicalBridgeLayout gateBridges;
    private BlimpLadderLayout ladder;
    private MushroomMineLayout mine;
    private ScorchedWellLayout well;
    private TyphonDiveLayout dive;
    private LighthouseLayout lighthouse;
    private PerimeterLayout perimeterLayout;
    private boolean relocated;
    WdaStructurePlan(ResourceLocation id,StructureStart start,IslandPlan island,boolean boarding,DungeonSpec.EntryMode entryMode,List<InteriorRoom> rooms,MechanicalBridgeLayout gateBridges,BlimpLadderLayout ladder,MushroomMineLayout mine,ScorchedWellLayout well,TyphonDiveLayout dive,LighthouseLayout lighthouse,PerimeterLayout perimeterLayout) {
        if(entryMode==DungeonSpec.EntryMode.START_DECK && AerialContentPolicy.needsGateBridge(id.toString()) && gateBridges==null)
            throw new IllegalArgumentException("Mechanical aerial entry requires its original connected bridges");
        this.id=id;this.start=start;this.island=island;this.boarding=boarding;this.entryMode=entryMode;this.interiorRooms=List.copyOf(rooms);
        this.gateBridges=gateBridges;
        if((entryMode==DungeonSpec.EntryMode.START_LADDER)!=(ladder!=null))throw new IllegalArgumentException("Missing or unexpected ladder route");
        this.ladder=ladder;
        if((entryMode==DungeonSpec.EntryMode.START_MINE)!=(mine!=null))throw new IllegalArgumentException("Missing or unexpected sparse mine route");
        this.mine=mine;
        if((entryMode==DungeonSpec.EntryMode.START_WELL)!=(well!=null))throw new IllegalArgumentException("Missing or unexpected original well route");this.well=well;
        if((entryMode==DungeonSpec.EntryMode.SHORE_DIVE)!=(dive!=null))throw new IllegalArgumentException("Missing or unexpected seabed approach");this.dive=dive;
        this.lighthouse = lighthouse;
        this.perimeterLayout = perimeterLayout;
    }
    public ResourceLocation id() { return id; }
    public StructureStart start() { return start; }
    public IslandPlan island() { return island; }
    public boolean boarding(){return boarding;}
    public DungeonSpec.EntryMode entryMode(){return entryMode;}
    public List<InteriorRoom> interiorRooms(){return interiorRooms;}
    public MechanicalBridgeLayout gateBridges(){return gateBridges;}
    public BlimpLadderLayout ladder(){return ladder;}
    public MushroomMineLayout mine(){return mine;}
    public ScorchedWellLayout well(){return well;}
    public TyphonDiveLayout dive(){return dive;}
    public LighthouseLayout lighthouse() { return lighthouse; }
    PerimeterLayout perimeterLayout() { return perimeterLayout; }
    public void relocateTo(BlockBounds allocatedContent) {
        if(relocated)throw new IllegalStateException("Plan already assigned to a pocket");
        var original=island.completeBounds();
        int x=Math.subtractExact(allocatedContent.minX(),original.minX());
        int y=Math.subtractExact(allocatedContent.minY(),original.minY());
        int z=Math.subtractExact(allocatedContent.minZ(),original.minZ());
        if(!original.move(x,y,z).equals(allocatedContent))throw new IllegalArgumentException("Reservation changed content dimensions");
        var pieces=start.getPieces();
        for(var piece:pieces) {
            var pool=(PoolElementStructurePiece)piece;
            List<JigsawJunction> moved=new ArrayList<>();
            for(var j:pool.getJunctions())moved.add(new JigsawJunction(Math.addExact(j.getSourceX(),x),
                    Math.addExact(j.getSourceGroundY(),y),Math.addExact(j.getSourceZ(),z),j.getDeltaY(),j.getDestProjection()));
            piece.move(x,y,z);
            // Vanilla move() does NOT move junction coordinates.
            pool.getJunctions().clear();moved.forEach(pool::addJunction);
        }
        // Rebuild to invalidate the cached bounds and keep the persisted start chunk inside our reservation.
        var anchor=new ChunkPos(pieces.get(0).getBoundingBox().getCenter());
        start=new StructureStart(start.getStructure(),anchor,0,new PiecesContainer(pieces));
        island=island.move(x,y,z);interiorRooms=interiorRooms.stream().map(r->r.move(x,y,z)).toList();
        if(gateBridges!=null)gateBridges=gateBridges.move(x,y,z);
        if(ladder!=null)ladder=ladder.move(x,y,z);
        if(mine!=null)mine=mine.move(x,y,z);
        if(well!=null)well=well.move(x,y,z);
        if(dive!=null)dive=dive.move(x,y,z);
        if (lighthouse != null) lighthouse = lighthouse.move(x, y, z);
        if (perimeterLayout != null) perimeterLayout = perimeterLayout.move(x,y,z);
        relocated=true;
    }
    static BlockBounds bounds(BoundingBox box) {
        return new BlockBounds(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ());
    }
}
