package dev.expedition.bridge.content;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.content.wda.InteriorBlockProbe;
import dev.expedition.bridge.portal.ManagedExitTarget;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.dimdev.dimdoors.block.entity.EntranceRiftBlockEntity;
import org.dimdev.dimdoors.world.ModDimensions;
import java.util.*;

/** Exits use the reserved island rim or validated existing room platforms. Never carve walls or replace machinery. */
public final class WdaExitInstaller {
    private record Exit(BlockPos pos,Direction facing){}
    public void install(MinecraftServer server,PortalRecord portal,IslandPlan island,int count) {
        if(!island.hasTerrain())throw new IllegalArgumentException("Void content needs validated original deck exits");
        var b=island.building();int y=island.firstFreeY();
        int midX=b.minX()+(b.width()-1)/2,midZ=b.minZ()+(b.depth()-1)/2;
        var candidates=List.of(new Exit(new BlockPos(island.entryX(),y,island.entryZ()),Direction.WEST),
                new Exit(new BlockPos(b.minX()-3,y,midZ),Direction.EAST),new Exit(new BlockPos(midX,y,b.minZ()-3),Direction.SOUTH),
                new Exit(new BlockPos(midX,y,b.maxZ()+3),Direction.NORTH));
        if(count<1 || count>4)throw new IllegalArgumentException("Invalid WDA exit count");
        installSelected(server,portal,candidates.subList(0,count));
    }
    public void installInterior(MinecraftServer server,PortalRecord portal,List<PortalRecord.Site> sites) {
        if(portal.selection()==null || portal.selection().content().provider()!=DungeonSpec.Provider.WDA
                || !portal.selection().content().environment().entryMode().interior()
                || sites.size()!=portal.selection().content().exits() || sites.isEmpty() || !sites.get(0).equals(portal.destination().arrival()))
            throw new IllegalArgumentException("Invalid original-room exits");
        var world=Objects.requireNonNull(server.getLevel(ModDimensions.DUNGEON));var probe=new InteriorBlockProbe(world);
        var cells=new ArrayList<BoardingRouteSearch.Cell>();
        for(var site:sites) {
            var cell=new BoardingRouteSearch.Cell(site.x(),site.y(),site.z());
            if(!site.dimension().equals(world.dimension().location().toString())
                    || !portal.destination().bounds().contains(site.x()-1,site.y()-1,site.z()-1)
                    || !portal.destination().bounds().contains(site.x()+1,site.y()+2,site.z()+1)
                    || !InteriorSiteSearch.clearPlatform(cell,probe) || cells.stream().anyMatch(c->!InteriorSiteSearch.separated(c,cell)))
                throw new IllegalStateException("Original room platform changed or is outside reservation: "+site);
            cells.add(cell);
        }
        installSelected(server,portal,sites.stream().map(s->new Exit(new BlockPos(s.x(),s.y(),s.z()),Direction.NORTH)).toList());
    }
    private void installSelected(MinecraftServer server,PortalRecord portal,List<Exit> candidates) {
        if(portal.phase()!=PortalRecord.Phase.GENERATING || !portal.exits().isEmpty() || candidates.isEmpty() || candidates.size()>4)
            throw new IllegalStateException("WDA exits must be installed exactly once before activation");
        var world=Objects.requireNonNull(server.getLevel(ModDimensions.DUNGEON));
        String[] woods={"cherry","oak","dark_oak","warped","crimson"};int tier=portal.effectiveTier();
        if(tier<1 || tier>5)throw new IllegalStateException("Missing selected difficulty");
        var block=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("dimdoors:block_ag_dim_minecraft_"+woods[tier-1]+"_door"));
        if(!(block instanceof DoorBlock))throw new IllegalStateException("Native dimensional wood door unavailable");
        for(var exit:candidates) {
            var p=exit.pos();var reserved=portal.destination().bounds();
            if(!reserved.contains(p.getX(),p.getY()-1,p.getZ()) || !reserved.contains(p.getX(),p.getY()+1,p.getZ()) || !world.getBlockState(p).isAir() || !world.getBlockState(p.above()).isAir()
                    || !world.getBlockState(p.below()).isFaceSturdy(world,p.below(),Direction.UP))
                throw new IllegalStateException("Reserved WDA exit site obstructed: "+p);
        }
        for(var exit:candidates) {
            var p=exit.pos();var lower=block.defaultBlockState().setValue(DoorBlock.FACING,exit.facing()).setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER);
            if(!world.setBlock(p,lower,18) || !world.setBlock(p.above(),lower.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),18)
                    || !(world.getBlockEntity(p) instanceof EntranceRiftBlockEntity rift))throw new IllegalStateException("WDA exit placement failed");
            rift.setDestination(new ManagedExitTarget(portal.id()));rift.register();rift.setChanged();
            portal.registerExit(new PortalRecord.Site(world.dimension().location().toString(),p.getX(),p.getY(),p.getZ()));
            world.blockUpdated(p,block);world.blockUpdated(p.above(),block);
        }
    }
}
