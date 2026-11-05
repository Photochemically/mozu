package dev.expedition.bridge.portal;

import com.mojang.serialization.Codec;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.Rotations;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.dimdev.dimdoors.api.rift.target.EntityTarget;
import org.dimdev.dimdoors.api.util.Location;
import org.dimdev.dimdoors.api.util.RGBA;
import org.dimdev.dimdoors.rift.targets.VirtualTarget;
import java.util.UUID;
import dev.expedition.bridge.storage.ExpeditionSavedData;
import dev.expedition.bridge.domain.PortalKind;

/** A world-generated entrance points to one persisted record; a copied door cannot create another. */
public final class ManagedEntranceTarget extends VirtualTarget implements EntityTarget {
    public static final Codec<ManagedEntranceTarget> CODEC=Codec.STRING.xmap(UUID::fromString,UUID::toString)
            .fieldOf("portal").xmap(ManagedEntranceTarget::new,ManagedEntranceTarget::portalId).codec();
    private static final RegistrySupplier<VirtualTargetType<ManagedEntranceTarget>> TYPE=
            VirtualTargetType.register("expedition_bridge:managed_entrance",CODEC,new RGBA(1,0.8f,0.1f,1));
    private final UUID portalId;
    public ManagedEntranceTarget(UUID portalId) { this.portalId=portalId; }
    public UUID portalId() { return portalId; }
    public static void registerType() {}
    @Override public VirtualTargetType<ManagedEntranceTarget> getType() { return TYPE.get(); }
    @Override public VirtualTarget copy() { return new ManagedEntranceTarget(portalId); }
    @Override public boolean receiveEntity(Entity entity, Vec3 relativePos, Rotations angle, Vec3 velocity, Location source) {
        if (!(entity instanceof ServerPlayer player)) return false;
        var portal=ExpeditionSavedData.get(player.server).get(portalId);
        if(portal==null)return false;
        if(portal.kind()!=PortalKind.GOLD)return FirstVisitService.INSTANCE.enter(player,portalId,getLocation());
        return GoldPortalService.enter(player,portalId,getLocation(),relativePos,angle,velocity,source);
    }
}
