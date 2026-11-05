package dev.expedition.bridge.content;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.portal.ManagedExitTarget;
import net.minecraft.server.MinecraftServer;
import org.dimdev.dimdoors.block.entity.RiftBlockEntity;
import org.dimdev.dimdoors.world.ModDimensions;
import org.dimdev.dimdoors.world.level.registry.DimensionalRegistry;
import org.dimdev.dimdoors.world.pocket.type.LazyGenerationPocket;
import java.util.ArrayList;

/** Converts the template's original rifts once, before visitors can add their own living-space doors. */
public final class NativeExitInstaller {
    private NativeExitInstaller(){}
    public static void rewardRoom(MinecraftServer server,PortalRecord portal) {
        if(portal.kind()!=PortalKind.GOLD || portal.phase()!=PortalRecord.Phase.GENERATING || !portal.exits().isEmpty())
            throw new IllegalStateException("Original reward exits can only be installed once before activation");
        var d=portal.destination();var b=d.bounds();var world=server.getLevel(ModDimensions.DUNGEON);
        var pocket=DimensionalRegistry.getPocketDirectory(ModDimensions.DUNGEON).getPocket(d.nativePocketId());
        if(world==null || pocket==null || b.width()>64 || b.depth()>64 || b.height()>64)
            throw new IllegalStateException("Unexpected reward room extent; needs a reviewed generation plan");
        for(int x=Math.floorDiv(b.minX(),16);x<=Math.floorDiv(b.maxX(),16);x++)
            for(int z=Math.floorDiv(b.minZ(),16);z<=Math.floorDiv(b.maxZ(),16);z++) {
                var chunk=world.getChunk(x,z);
                if(pocket instanceof LazyGenerationPocket lazy)lazy.chunkLoaded(chunk);
            }
        if(pocket instanceof LazyGenerationPocket lazy && !lazy.isDoneGenerating())throw new IllegalStateException("Reward template has unfinished lazy chunks");
        var originals=new ArrayList<RiftBlockEntity>();
        for(var entity:pocket.getBlockEntities().values()) {
            var p=entity.getBlockPos();
            if(entity instanceof RiftBlockEntity rift && b.contains(p.getX(),p.getY(),p.getZ()))originals.add(rift);
        }
        if(originals.isEmpty())throw new IllegalStateException("Reward room has no exits");
        NativeRiftInstaller.install(server,portal,originals.stream().map(RiftBlockEntity::getBlockPos).toList());
        if(!portal.exits().contains(d.arrival()))throw new IllegalStateException("Reward entrance was not converted to a managed return exit");
    }
}
