package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Cooperative server-thread job. A native template/chunk call is indivisible, so the time budget is a soft limit. */
final class WdaBuildJob {
    enum Stage { ISLAND, WATER, STRUCTURE, BOARDING, INTERIOR_SITES, DIVE_SITES, PERIMETER_ROUTE, INDEX, FLUID_RELEASE, CONTENT_READY }
    final PortalRecord portal;
    final WdaStructurePlan plan;
    final ServerLevel world;
    private final IslandCursor island;
    private final IslandCursor water;
    private final ConstructionFluidBuffer fluidTicks;
    private WdaBoardingJob boarding;
    private WdaInteriorSitesJob interior;
    private TyphonDiveSitesJob dive;
    private BooleanSupplier perimeterStep;
    private final int minChunkX,maxChunkX,minChunkZ,maxChunkZ;
    private int chunkX,chunkZ;
    private Stage stage=Stage.ISLAND;
    private final StructureIndexGrid indexGrid;
    private final Set<StructureIndexGrid.Anchor> indexed=new HashSet<>();
    private long blocks,chunkPlacements,maxUnitNanos;

    WdaBuildJob(ServerLevel world,PortalRecord portal,WdaStructurePlan plan) {
        this.world=world;this.portal=portal;this.plan=plan;island=new IslandCursor(plan.island());
        water=new IslandCursor(plan.island(),true);
        fluidTicks=new ConstructionFluidBuffer(portal.destination().bounds(),plan.island().waterDepth()>0 || plan.well()!=null || WindmillLayout.TARGET.equals(plan.id().toString())
                || plan.perimeterLayout() instanceof BathhouseLayout,plan.well()!=null);
        var b=plan.island().building();
        minChunkX=Math.floorDiv(b.minX(),16);maxChunkX=Math.floorDiv(b.maxX(),16);
        minChunkZ=Math.floorDiv(b.minZ(),16);maxChunkZ=Math.floorDiv(b.maxZ(),16);
        indexGrid=new StructureIndexGrid(minChunkX,minChunkZ,maxChunkX,maxChunkZ);
        rewind();
    }
    Stage stage() { return stage; }
    long blocks() { return blocks; }
    long chunkPlacements() { return chunkPlacements; }
    long maxUnitNanos() { return maxUnitNanos; }
    List<PortalRecord.Site> interiorSites(){return dive!=null?dive.sites():interior==null?List.of():interior.sites();}
    boolean deferFluidTick(BlockPos pos,FluidState fluid) {
        return fluidTicks.defer(pos.getX(),pos.getY(),pos.getZ(),kind(fluid));
    }
    private static ConstructionFluidBuffer.Kind kind(FluidState fluid){return fluid.is(FluidTags.WATER)?ConstructionFluidBuffer.Kind.WATER:fluid.is(FluidTags.LAVA)?ConstructionFluidBuffer.Kind.LAVA:ConstructionFluidBuffer.Kind.OTHER;}

    void step(int maxBlocks,long budgetNanos) {
        if(!world.getServer().isSameThread())throw new IllegalStateException("World writes off server thread");
        if(maxBlocks<=0 || budgetNanos<=0)throw new IllegalArgumentException("Invalid generation budget");
        long started=System.nanoTime();
        try {
            if(stage==Stage.ISLAND || stage==Stage.WATER) {
                var cursor=stage==Stage.ISLAND?island:water;
                int written=0;
                while(written<maxBlocks && (written==0 || System.nanoTime()-started<budgetNanos) && cursor.hasNext()) {
                    var c=cursor.next();var pos=new BlockPos(c.x(),c.y(),c.z());
                    var state=material(c);
                    // Earlier water sources may fill later cells through fluid ticks. Never replace another fluid/block.
                    if(stage==Stage.WATER && world.getBlockState(pos).getBlock()==Blocks.WATER) {
                        if(!world.getBlockState(pos).equals(state) && !world.setBlock(pos,state,2|16))
                            throw new IllegalStateException("Failed to finish basin source at "+pos);
                        written++;continue;
                    }
                    if(!world.getBlockState(pos).isAir())
                        throw new IllegalStateException("Allocated terrain is occupied; refusing overwrite at "+pos);
                    if(!world.setBlock(pos,state,2|16))
                        throw new IllegalStateException("Failed to place island terrain at "+pos);
                    written++;blocks++;
                }
                if(!cursor.hasNext()) {
                    stage=stage==Stage.ISLAND && plan.island().waterDepth()>0?Stage.WATER:Stage.STRUCTURE;
                    rewind();
                }
                return;
            }
            if(stage==Stage.STRUCTURE) {
                var chunk=new ChunkPos(chunkX,chunkZ);
                var clip=new BoundingBox(chunk.getMinBlockX(),world.getMinBuildHeight(),chunk.getMinBlockZ(),
                        chunk.getMaxBlockX(),world.getMaxBuildHeight()-1,chunk.getMaxBlockZ());
                var region=new IslandPlacementRegion(world,world.getChunk(chunkX,chunkZ),portal.destination().bounds(),
                        clip,plan.island().levels(),portal.seed(),!plan.island().hasTerrain());
                // Keep native block entities, entities, processors, jigsaw replacement and afterPlace behavior.
                plan.start().placeInChunk(region,world.structureManager(),world.getChunkSource().getGenerator(),
                        RandomSource.create(portal.seed() ^ chunk.toLong()),clip,chunk);
                chunkPlacements++;
                if(!advance()) {
                    stage=plan.boarding()?Stage.BOARDING:Stage.INDEX;
                    if(plan.boarding())boarding=new WdaBoardingJob(world,portal,plan.island());
                    if (plan.lighthouse() != null) {
                        stage = Stage.PERIMETER_ROUTE;
                        perimeterStep = new LighthouseRoute(plan.lighthouse(), plan.island(),
                                portal.selection().content().exits(), new LighthouseProbe(world))::step;
                    } else if (WindmillLayout.TARGET.equals(plan.id().toString())) {
                        stage = Stage.PERIMETER_ROUTE;
                        perimeterStep = new WindmillRoute(WindmillLayout.fromRoot(plan.start()), plan.island(),
                                portal.selection().content().exits(), new WindmillProbe(world))::step;
                    } else if (plan.perimeterLayout() != null) {
                        stage = Stage.PERIMETER_ROUTE;
                        perimeterStep = plan.perimeterLayout().survey(world, plan.island(), portal.selection().content().exits());
                    } else if(plan.dive()!=null) {
                        stage=Stage.DIVE_SITES;
                        dive=new TyphonDiveSitesJob(world,plan.dive(),plan.island(),portal.selection().content().exits());
                    } else if(plan.entryMode().interior()) {
                        stage=Stage.INTERIOR_SITES;
                        interior=new WdaInteriorSitesJob(world,plan.interiorRooms(),portal.selection().content().exits(),
                                plan.entryMode()==DungeonSpec.EntryMode.START_DECK || plan.ladder()!=null || plan.mine()!=null,plan.gateBridges(),plan.ladder(),plan.mine(),plan.well());
                    }
                    rewind();
                }
                return;
            }
            if(stage==Stage.BOARDING) {
                if(boarding.step(256,budgetNanos)){stage=Stage.INDEX;rewind();}
                return;
            }
            if(stage==Stage.INTERIOR_SITES) {
                if(interior.step(256,budgetNanos)){stage=Stage.INDEX;rewind();}
                return;
            }
            if(stage==Stage.DIVE_SITES) {
                if(dive.step(256,budgetNanos)){stage=Stage.INDEX;rewind();}
                return;
            }
            if (stage == Stage.PERIMETER_ROUTE) {
                for (int n = 0; n < 256 && (n == 0 || System.nanoTime() - started < budgetNanos); n++) {
                    if (perimeterStep.getAsBoolean()) { stage = Stage.INDEX; rewind(); break; }
                }
                return;
            }
            if(stage==Stage.INDEX) {
                var start=plan.start();var structure=start.getStructure();
                var index=indexGrid.anchorFor(chunkX,chunkZ);var anchorPos=new ChunkPos(index.x(),index.z());
                if(!indexed.contains(index)) {
                    var anchor=world.getChunk(index.x(),index.z());
                    var existing=anchor.getStartForStructure(structure);
                    if(existing!=null && existing.isValid())throw new IllegalStateException("Structure start collision; refusing overwrite");
                    // Same complete graph, one local reference per chunk; never rerun placement for the replicas.
                    anchor.setStartForStructure(structure,new StructureStart(structure,anchorPos,0,new PiecesContainer(start.getPieces())));
                    anchor.setUnsaved(true);indexed.add(index);
                }
                // Without these references WDA's full/piece spawn overrides disappear after manual placement.
                var chunk=world.getChunk(chunkX,chunkZ);
                chunk.addReferenceForStructure(structure,anchorPos.toLong());chunk.setUnsaved(true);
                if(!advance()) {
                    if(interior!=null||dive!=null)portal.resolveWdaStartRoomArrival(interiorSites().get(0));
                    var a=portal.destination().arrival();var feet=new BlockPos(a.x(),a.y(),a.z());
                    if(!world.getBlockState(feet).isAir() || !world.getBlockState(feet.above()).isAir()
                            || !world.getBlockState(feet.below()).isFaceSturdy(world,feet.below(),Direction.UP)
                            || !world.getFluidState(feet.below()).isEmpty())
                        throw new IllegalStateException("Planned entry is obstructed; cannot release dungeon to transit");
                    fluidTicks.beginRelease();
                    stage=Stage.FLUID_RELEASE;
                }
                return;
            }
            if(stage==Stage.FLUID_RELEASE) {
                int released=0;
                while(released<maxBlocks && fluidTicks.remaining()>0 && (released==0 || System.nanoTime()-started<budgetNanos)) {
                    released+=fluidTicks.release(1,c->{
                        var pos=new BlockPos(c.x(),c.y(),c.z());var fluid=world.getFluidState(pos);
                        if(fluidTicks.manages(kind(fluid)))world.scheduleTick(pos,fluid.getType(),fluid.is(FluidTags.LAVA)?fluid.getType().getTickDelay(world):1);
                    });
                }
                if(fluidTicks.remaining()==0)stage=Stage.CONTENT_READY;
            }
        } finally { maxUnitNanos=Math.max(maxUnitNanos,System.nanoTime()-started); }
    }
    private BlockState material(IslandCursor.Cell cell) {
        return switch(plan.island().material(cell.x(),cell.y(),cell.z())) {
            case GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
            case DIRT -> Blocks.DIRT.defaultBlockState();
            case STONE -> Blocks.STONE.defaultBlockState();
            case WATER -> Blocks.WATER.defaultBlockState();
        };
    }
    private void rewind() { chunkX=minChunkX;chunkZ=minChunkZ; }
    private boolean advance() {
        if(++chunkX>maxChunkX){chunkX=minChunkX;chunkZ++;}
        return chunkZ<=maxChunkZ;
    }
}
