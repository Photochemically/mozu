package dev.expedition.bridge.content;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.portal.ManagedExitTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.dimdev.dimdoors.block.entity.RiftBlockEntity;
import org.dimdev.dimdoors.world.ModDimensions;
import org.dimdev.dimdoors.world.level.registry.DimensionalRegistry;
import org.dimdev.dimdoors.world.pocket.type.LazyGenerationPocket;
import java.util.*;

/** First finish original lazy chunks, then rewrite captured original rifts. No whole-pocket scan or resident tickets. */
public final class NativeRoomBuild {
    private final MinecraftServer server;
    private final PortalRecord portal;
    private final int minX,maxX,maxZ;
    private int x,z;
    private final List<BlockPos> originalRifts=new ArrayList<>();
    private boolean generated;
    public NativeRoomBuild(MinecraftServer server,PortalRecord portal) {
        this.server=server;this.portal=portal;var b=portal.destination().bounds();
        minX=Math.floorDiv(b.minX(),16);maxX=Math.floorDiv(b.maxX(),16);maxZ=Math.floorDiv(b.maxZ(),16);
        x=minX;z=Math.floorDiv(b.minZ(),16);
    }
    public boolean step() {
        if(!server.isSameThread() || portal.phase()!=PortalRecord.Phase.GENERATING)throw new IllegalStateException("Native build no longer active");
        var d=portal.destination();var b=d.bounds();var world=Objects.requireNonNull(server.getLevel(ModDimensions.DUNGEON));
        var pocket=DimensionalRegistry.getPocketDirectory(ModDimensions.DUNGEON).getPocket(d.nativePocketId());
        if(!generated) {
            var chunk=world.getChunk(x,z);if(pocket instanceof LazyGenerationPocket lazy)lazy.chunkLoaded(chunk);
            chunk.getBlockEntities().forEach((p,be)->{if(be instanceof RiftBlockEntity && b.contains(p.getX(),p.getY(),p.getZ()))originalRifts.add(p.immutable());});
            if(++x>maxX){x=minX;z++;}
            if(z>maxZ) {
                if(pocket instanceof LazyGenerationPocket lazy && !lazy.isDoneGenerating())throw new IllegalStateException("Native generator left unfinished chunks");
                if(originalRifts.isEmpty())throw new IllegalStateException("Native room has no return rifts");
                originalRifts.sort(Comparator.<BlockPos>comparingInt(p -> p.getX()).thenComparingInt(p -> p.getZ()).thenComparingInt(p -> p.getY()));
                generated=true;
            }
            return false;
        }
        NativeRiftInstaller.install(server,portal,originalRifts);
        if(!portal.exits().contains(d.arrival()))throw new IllegalStateException("Native arrival was not included among managed exits");
        return true;
    }
}
