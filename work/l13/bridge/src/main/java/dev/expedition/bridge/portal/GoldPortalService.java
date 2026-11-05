package dev.expedition.bridge.portal;

import dev.expedition.bridge.ExpeditionBridge;
import dev.expedition.bridge.content.NativeRewardRoomProvider;
import dev.expedition.bridge.content.PocketIdentity;
import dev.expedition.bridge.content.NativeExitInstaller;
import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.storage.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Rotations;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.dimdev.dimdoors.api.rift.target.EntityTarget;
import org.dimdev.dimdoors.api.util.Location;
import org.dimdev.dimdoors.world.ModDimensions;
import org.dimdev.dimdoors.world.level.registry.DimensionalRegistry;
import java.util.UUID;

public final class GoldPortalService {
    private static final NativeRewardRoomProvider REWARD=new NativeRewardRoomProvider();
    private GoldPortalService() {}

    public static boolean enter(ServerPlayer player, UUID id, Location entrance, Vec3 relativePos, Rotations angle, Vec3 velocity, Location source) {
        var server=player.server;
        var data=ExpeditionSavedData.get(server);
        var portal=data.get(id);
        // Check the physical source, not just a copied UUID stored on a portable item.
        if(portal==null || portal.kind()!=PortalKind.GOLD || entrance==null
                || !portal.entrance().equals(site(entrance)) || !nearSource(player,portal.entrance())) return false;
        boolean allocating=false;
        try {
            if(portal.phase()==PortalRecord.Phase.NEW) {
                REWARD.validate(server,portal);
                allocating=true;
                portal.preparationStarted(NativeRewardRoomProvider.TEMPLATE.toString());data.setDirty();
                PortalCheckpoint.write(server,data);
                REWARD.generate(server,portal);
                portal.generationStarted();
                data.setDirty();PortalCheckpoint.write(server,data);
                NativeExitInstaller.rewardRoom(server,portal);
                GenerationActivation.activate(server,portal);
                allocating=false;
            }
            if(portal.exits().isEmpty())return false; // Legacy dev rooms require explicit migration; never leave their chain routes active.
            return ManagedTransit.enterGold(player,portal);
        } catch(Exception failure) {
            if(allocating) {
                portal.quarantine(failure.toString());data.setDirty();
                try { PortalCheckpoint.write(server,data); } catch(java.io.IOException checkpoint){failure.addSuppressed(checkpoint);}
            }
            ExpeditionBridge.LOGGER.error("Gold portal {} failed; no automatic regeneration or pocket deletion",id,failure);
            player.displayClientMessage(Component.literal("奖励房暂不可用，请查看服务器记录。已有空间不会被重置。"),false);
            return false;
        }
    }

    private static boolean nearSource(ServerPlayer player,PortalRecord.Site source) {
        var p=player.blockPosition();return player.level().dimension().location().toString().equals(source.dimension())
                && Math.abs((long)p.getX()-source.x())<=3 && Math.abs((long)p.getY()-source.y())<=3 && Math.abs((long)p.getZ()-source.z())<=3;
    }

    public static boolean nativeIdentityMatches(MinecraftServer server, PortalRecord portal) {
        var d=portal.destination();
        if(d==null || !d.dimension().equals(ModDimensions.DUNGEON.location().toString()))return false;
        var pocket=DimensionalRegistry.getPocketDirectory(ModDimensions.DUNGEON).getPocket(d.nativePocketId());
        if(pocket==null || !pocket.hasAddon(PocketIdentity.ID))return false;
        PocketIdentity identity=pocket.getAddon(PocketIdentity.ID);
        var b=pocket.getBox();
        return portal.id().equals(identity.portalId()) && d.bounds().equals(new BlockBounds(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()));
    }
    public static boolean inside(PortalRecord portal, ServerPlayer player) {
        var d=portal.destination();var p=player.blockPosition();
        return d!=null && d.dimension().equals(player.level().dimension().location().toString()) && d.bounds().contains(p.getX(),p.getY(),p.getZ());
    }
    public static PortalRecord.Site site(Location location) {
        return new PortalRecord.Site(location.world.location().toString(),location.pos.getX(),location.pos.getY(),location.pos.getZ());
    }
}
