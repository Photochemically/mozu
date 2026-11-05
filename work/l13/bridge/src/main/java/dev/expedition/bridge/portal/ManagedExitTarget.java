package dev.expedition.bridge.portal;

import com.mojang.serialization.Codec;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.Rotations;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.dimdev.dimdoors.api.rift.target.EntityTarget;
import org.dimdev.dimdoors.api.util.*;
import org.dimdev.dimdoors.rift.targets.VirtualTarget;
import java.util.UUID;

/** Source location must be one of the saved exits; copied target NBT grants no teleport authority. */
public final class ManagedExitTarget extends VirtualTarget implements EntityTarget {
    public static final Codec<ManagedExitTarget> CODEC=Codec.STRING.xmap(UUID::fromString,UUID::toString)
            .fieldOf("portal").xmap(ManagedExitTarget::new,ManagedExitTarget::portalId).codec();
    private static final RegistrySupplier<VirtualTargetType<ManagedExitTarget>> TYPE=
            VirtualTargetType.register("expedition_bridge:managed_exit",CODEC,new RGBA(0.3f,0.8f,1,1));
    private final UUID portalId;
    public ManagedExitTarget(UUID portalId){this.portalId=portalId;}
    public UUID portalId(){return portalId;}
    public static void registerType(){}
    @Override public VirtualTargetType<ManagedExitTarget> getType(){return TYPE.get();}
    @Override public VirtualTarget copy(){return new ManagedExitTarget(portalId);}
    @Override public boolean receiveEntity(Entity entity,Vec3 pos,Rotations angle,Vec3 velocity,Location source) {
        return entity instanceof ServerPlayer player && ManagedTransit.leave(player,portalId,getLocation());
    }
}
