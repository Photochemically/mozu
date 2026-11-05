package dev.expedition.bridge.content.wda;

import dev.expedition.bridge.domain.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import org.dimdev.dimdoors.world.ModDimensions;
import java.util.*;

/** Calls the registered original generator, including WDA's custom keep/lighthouse implementations. */
public final class WdaStructurePlanner {
    /** Environment inputs are explicit catalog choices, not inferred from tier or silently fixed balance defaults. */
    public record Environment(int firstFreeY,int padding,int rim,int foundationDepth,IslandPlan.Surface surface,int waterDepth,boolean boarding,DungeonSpec.EntryMode entryMode) {
        public Environment(int firstFreeY,int padding,int rim,int foundationDepth,IslandPlan.Surface surface) {
            this(firstFreeY,padding,rim,foundationDepth,surface,0,false,DungeonSpec.EntryMode.PERIMETER);
        }
        public Environment(int firstFreeY,int padding,int rim,int foundationDepth,IslandPlan.Surface surface,int waterDepth) {
            this(firstFreeY,padding,rim,foundationDepth,surface,waterDepth,false,DungeonSpec.EntryMode.PERIMETER);
        }
        public Environment(int firstFreeY,int padding,int rim,int foundationDepth,IslandPlan.Surface surface,int waterDepth,boolean boarding) {
            this(firstFreeY,padding,rim,foundationDepth,surface,waterDepth,boarding,DungeonSpec.EntryMode.PERIMETER);
        }
        public Environment {new DungeonSpec.Environment(firstFreeY,padding,rim,foundationDepth,surface,waterDepth,boarding,entryMode);}
    }

    /** Read-only planning evidence, not proof of placement, traversal, loot or combat correctness. */
    public record Inspection(String structure,long seed,int pieces,int terrainMatchingPieces,BlockBounds building,
                             BlockBounds complete,int requiredHeight,int availableHeight,boolean heightFits,
                             long terrainBoxVolumeUpperBound,long islandChunkFootprint) {}

    public WdaStructurePlan prepare(MinecraftServer server,ResourceLocation id,long seed,Environment environment) {
        var plan=plan(server,id,seed,environment);var target=server.getLevel(ModDimensions.DUNGEON);
        ReservationGeometry.plan(plan.island().completeBounds(),16,target.getMinBuildHeight(),target.getMaxBuildHeight());
        return plan;
    }

    public Inspection inspect(MinecraftServer server,ResourceLocation id,long seed,Environment environment) {
        var plan=plan(server,id,seed,environment);var island=plan.island();var bounds=island.completeBounds();
        var target=server.getLevel(ModDimensions.DUNGEON);int required=bounds.expand(16).height(),available=target.getHeight();
        var terrain=island.terrainBounds();
        long volume=island.hasTerrain()?Math.multiplyExact(Math.multiplyExact((long)terrain.width(),terrain.depth()),terrain.height()):0;
        long chunks=Math.multiplyExact((long)Math.floorDiv(bounds.maxX(),16)-Math.floorDiv(bounds.minX(),16)+1,
                (long)Math.floorDiv(bounds.maxZ(),16)-Math.floorDiv(bounds.minZ(),16)+1);
        int matching=(int)plan.start().getPieces().stream().map(p->(PoolElementStructurePiece)p)
                .filter(p->p.getElement().getProjection()==StructureTemplatePool.Projection.TERRAIN_MATCHING).count();
        return new Inspection(id.toString(),seed,plan.start().getPieces().size(),matching,island.building(),bounds,
                required,available,required<=available,volume,chunks);
    }

    private WdaStructurePlan plan(MinecraftServer server,ResourceLocation id,long seed,Environment environment) {
        if(!server.isSameThread())throw new IllegalStateException("Structure planning off server thread");
        // Match catalog bounds even when a diagnostic command supplies values directly.
        new DungeonSpec.Environment(environment.firstFreeY(),environment.padding(),environment.rim(),environment.foundationDepth(),environment.surface(),environment.waterDepth(),environment.boarding(),environment.entryMode());
        if(!id.getNamespace().equals("dungeons_arise"))throw new IllegalArgumentException("WDA provider only accepts WDA structures");
        var world=server.overworld();var target=server.getLevel(ModDimensions.DUNGEON);
        if(target==null)throw new IllegalStateException("Dungeon dimension unavailable");
        var registries=server.registryAccess();
        var gaps=WdaKnownResourceGaps.missing(id.toString(),
                pool->registries.registryOrThrow(Registries.TEMPLATE_POOL).containsKey(ResourceLocation.parse(pool)),
                template->{var key=ResourceLocation.parse(template);return server.getResourceManager()
                        .getResource(ResourceLocation.fromNamespaceAndPath(key.getNamespace(),"structures/"+key.getPath()+".nbt")).isPresent();});
        if(!gaps.isEmpty())throw new IllegalArgumentException("Known upstream resource gaps remain; explicit repair required, no partial building: "+gaps);
        Structure structure=registries.registryOrThrow(Registries.STRUCTURE).get(id);
        if(structure==null)throw new IllegalArgumentException("Unknown structure "+id);
        var type=registries.registryOrThrow(Registries.STRUCTURE_TYPE).getKey(structure.type());
        if(type==null || !(type.toString().equals("minecraft:jigsaw") || type.toString().equals("dungeons_arise:generic_structures")))
            throw new IllegalArgumentException("Unsupported structure generator "+type);
        boolean aerial=environment.surface()==IslandPlan.Surface.VOID;
        if(aerial) {
            var encoded=Structure.DIRECT_CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE,registries),structure)
                    .getOrThrow(false,message->{throw new IllegalArgumentException(message);});
            if(!(encoded instanceof CompoundTag tag))throw new IllegalArgumentException("Uninspectable aerial structure definition");
            AerialContentPolicy.validate(id.toString(),type.toString(),tag.contains("project_start_to_heightmap"));
        }
        var source=world.getChunkSource();
        var terrain=new PlanningTerrain(source.getGenerator().getBiomeSource(),environment.firstFreeY(),
                world.getMinBuildHeight(),world.getHeight(),environment.waterDepth(),aerial);
        var start=structure.generate(registries,terrain,terrain.getBiomeSource(),source.randomState(),
                server.getStructureManager(),seed,new ChunkPos(0,0),0,world,biome->true);
        if(!start.isValid())throw new IllegalStateException("Native generator returned no building for "+id);
        BlockBounds complete=WdaStructurePlan.bounds(start.getBoundingBox());
        for(var piece:start.getPieces()) {
            if(!(piece instanceof PoolElementStructurePiece pool)
                    || (pool.getElement().getClass()!=SinglePoolElement.class && pool.getElement().getClass()!=LegacySinglePoolElement.class))
                throw new IllegalStateException("Unsupported piece; refusing partial placement: "+piece.getClass().getName());
            validateElement(server,registries,pool.getElement());
            if(aerial && pool.getElement().getProjection()!=StructureTemplatePool.Projection.RIGID)
                throw new IllegalArgumentException("Terrain-matching aerial piece requires separate adaptation; no flattening or omission");
            // Terrain matching's native GravityProcessor replaces Y with heightmap - 1 + template local Y.
            // Keep original bounds for native semantics, but reserve the entire actual placement envelope too.
            if(pool.getElement().getProjection()==StructureTemplatePool.Projection.TERRAIN_MATCHING) {
                var box=piece.getBoundingBox();
                int height=pool.getElement().getSize(server.getStructureManager(),pool.getRotation()).getY();
                complete=IslandPlan.union(complete,new BlockBounds(box.minX(),environment.firstFreeY()-1,box.minZ(),
                        box.maxX(),Math.addExact(environment.firstFreeY(),height)-2,box.maxZ()));
            }
        }
        var island=new IslandPlan(complete,environment.firstFreeY(),environment.padding(),environment.rim(),
                environment.foundationDepth(),environment.surface(),environment.waterDepth());
        List<InteriorRoom> rooms=new ArrayList<>();
        if(environment.entryMode().interior()) {
            if(!aerial)InteriorRoom.requireRockCover(island);
            int index=0;
            for(var piece:start.getPieces()) {
                var pool=(PoolElementStructurePiece)piece;var bounds=WdaStructurePlan.bounds(piece.getBoundingBox());
                List<BoardingRouteSearch.Cell> passages=new ArrayList<>();
                if(pool.getElement().getProjection()==StructureTemplatePool.Projection.RIGID && bounds.width()>=5 && bounds.depth()>=5 && bounds.height()>=4) {
                    // Reading joints neither places templates nor consumes the generator's random stream.
                    for(var joint:pool.getElement().getShuffledJigsawBlocks(server.getStructureManager(),pool.getPosition(),pool.getRotation(),RandomSource.create(seed ^ index))) {
                        if(joint.nbt()!=null && (aerial || joint.nbt().getString("final_state").equals("minecraft:air"))
                                && JigsawBlock.getFrontFacing(joint.state()).getAxis().isHorizontal()) {
                            var p=joint.pos();passages.add(new BoardingRouteSearch.Cell(p.getX(),p.getY(),p.getZ()));
                        }
                    }
                }
                if(index++==0 && passages.isEmpty())throw new IllegalArgumentException("Starting piece has no audited horizontal passage; no substitute start room");
                if(!passages.isEmpty())rooms.add(new InteriorRoom(bounds,passages));
            }
        }
        var gateBridges=aerial && AerialContentPolicy.needsGateBridge(id.toString())?MechanicalBridgePlanner.prepare(server,start):null;
        return new WdaStructurePlan(id,start,island,environment.boarding(),environment.entryMode(),rooms,gateBridges);
    }

    private void validateElement(MinecraftServer server,RegistryAccess registries,StructurePoolElement element) {
        var encoded=StructurePoolElement.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE,registries),element)
                .getOrThrow(false,message->{throw new IllegalArgumentException(message);});
        if(!(encoded instanceof CompoundTag tag) || !tag.contains("processors",Tag.TAG_STRING))
            throw new IllegalArgumentException("Inline/unknown processor lists require an explicit bounds audit");
        if(!tag.contains("location",Tag.TAG_STRING))throw new IllegalArgumentException("Inline structure template requires explicit audit");
        var location=ResourceLocation.parse(tag.getString("location"));
        var template=server.getStructureManager().get(location);
        // Vanilla getOrCreate can cache an empty template for missing assets during planning. Never silently accept it.
        if(template.isEmpty() || template.get().getSize().getX()<=0 || template.get().getSize().getY()<=0 || template.get().getSize().getZ()<=0)
            throw new IllegalArgumentException("Missing or empty native structure template; refusing partial building: "+location);
        var processors=registries.registryOrThrow(Registries.PROCESSOR_LIST).get(ResourceLocation.parse(tag.getString("processors")));
        if(processors==null)throw new IllegalArgumentException("Unresolved structure processors");
        for(var processor:processors.list())if(!(processor instanceof RuleProcessor))
            throw new IllegalArgumentException("Unaudited positional processor: "+processor.getClass().getName());
    }
}
