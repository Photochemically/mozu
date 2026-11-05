package dev.expedition.bridge.storage;

import dev.expedition.bridge.domain.PortalRecord;
import dev.expedition.bridge.domain.PortalKind;
import dev.expedition.bridge.portal.GoldPortalService;
import dev.expedition.bridge.portal.ManagedExitTarget;
import dev.expedition.bridge.content.NativeRiftInstaller;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import org.dimdev.dimdoors.block.entity.RiftBlockEntity;
import java.io.IOException;

/** Exit validation and native save barrier are shared by reward rooms and the future WDA transit consumer. */
public final class GenerationActivation {
    private GenerationActivation(){}
    public static void activate(MinecraftServer server,PortalRecord portal) throws IOException {
        if(portal.phase()!=PortalRecord.Phase.GENERATING || portal.exits().isEmpty() || !GoldPortalService.nativeIdentityMatches(server,portal)
                || (portal.kind()!=PortalKind.GOLD && portal.selection()==null))
            throw new IllegalStateException("Instance cannot activate before identity and exits are ready");
        var d=portal.destination();var world=server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(d.dimension())));
        for(var exit:portal.exits()) {
            if(!(world.getBlockEntity(new BlockPos(exit.x(),exit.y(),exit.z())) instanceof RiftBlockEntity rift)
                    || !(rift.getDestination() instanceof ManagedExitTarget target) || !target.portalId().equals(portal.id()))
                throw new IllegalStateException("Missing or mismatched managed exit");
        }
        for(var link:portal.internalLinks().entrySet()) {
            var source=link.getKey();var target=link.getValue();
            if(!(world.getBlockEntity(new BlockPos(source.x(),source.y(),source.z())) instanceof RiftBlockEntity rift)
                    || !target.equals(NativeRiftInstaller.localTarget(rift,source))
                    || !(world.getBlockEntity(new BlockPos(target.x(),target.y(),target.z())) instanceof RiftBlockEntity))
                throw new IllegalStateException("Native internal mechanism changed before activation");
        }
        var data=ExpeditionSavedData.get(server);data.setDirty();PortalCheckpoint.write(server,data);
        // Save all native dimensions/capabilities through Forge's own hooks, before journaling READY.
        // This one-time flush can stall; runtime load and cross-file recovery remain acceptance items.
        if(!server.saveEverything(true,true,true))throw new IOException("Native world save did not execute");
        portal.generationComplete();data.setDirty();PortalCheckpoint.write(server,data);
    }
}
