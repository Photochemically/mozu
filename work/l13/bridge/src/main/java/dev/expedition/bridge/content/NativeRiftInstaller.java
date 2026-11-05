package dev.expedition.bridge.content;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.portal.ManagedExitTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.dimdev.dimdoors.block.entity.RiftBlockEntity;
import org.dimdev.dimdoors.rift.targets.*;
import org.dimdev.dimdoors.world.ModDimensions;
import java.util.*;

/** Captured original rifts only. Never scans or rewrites doors built later by players. */
public final class NativeRiftInstaller {
    private NativeRiftInstaller() {}
    public static void install(MinecraftServer server,PortalRecord portal,Collection<BlockPos> positions) {
        if(!server.isSameThread() || portal.phase()!=PortalRecord.Phase.GENERATING || !portal.exits().isEmpty() || !portal.internalLinks().isEmpty())
            throw new IllegalStateException("Native routes can only be installed once before activation");
        var world=Objects.requireNonNull(server.getLevel(ModDimensions.DUNGEON));
        var rifts=new LinkedHashMap<PortalRecord.Site,RiftBlockEntity>();
        var locals=new LinkedHashMap<PortalRecord.Site,PortalRecord.Site>();var inactive=new HashSet<PortalRecord.Site>();
        for(var pos:positions) {
            if(!world.hasChunkAt(pos) || !(world.getBlockEntity(pos) instanceof RiftBlockEntity rift))
                throw new IllegalStateException("Original native rift unavailable");
            var source=site(world.dimension().location().toString(),pos);rifts.put(source,rift);
            var target=localTarget(rift,source);if(target!=null)locals.put(source,target);
            if(rift.getDestination()==null || rift.getDestination()==VirtualTarget.NoneTarget.INSTANCE)inactive.add(source);
        }
        // Plan and validate every internal endpoint before changing the first exit.
        var plan=NativeRiftPlan.create(portal.destination(),rifts.keySet(),locals,inactive,portal.kind()==PortalKind.GOLD);
        for(var source:plan.exits()) {
            rifts.get(source).setDestination(new ManagedExitTarget(portal.id()));portal.registerExit(source);
        }
        plan.internalLinks().forEach((source,target)->{portal.registerInternalLink(source,target);rifts.get(source).setChanged();});
    }
    public static PortalRecord.Site localTarget(RiftBlockEntity rift,PortalRecord.Site source) {
        var destination=rift.getDestination();
        if(destination instanceof RelativeReference relative) {
            var offset=relative.getOffset();
            return new PortalRecord.Site(source.dimension(),Math.addExact(source.x(),offset.getX()),
                    Math.addExact(source.y(),offset.getY()),Math.addExact(source.z(),offset.getZ()));
        }
        if(destination instanceof LocalReference local)return site(source.dimension(),local.getTarget());
        return null; // Do not resolve wrapped/generative targets or load another pocket to inspect them.
    }
    private static PortalRecord.Site site(String dimension,BlockPos pos) {return new PortalRecord.Site(dimension,pos.getX(),pos.getY(),pos.getZ());}
}
