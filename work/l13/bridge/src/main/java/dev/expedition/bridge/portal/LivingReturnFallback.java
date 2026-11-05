package dev.expedition.bridge.portal;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.dimdev.dimdoors.world.level.registry.DimensionalRegistry;

/** Called only where native private-pocket exit would otherwise send a broken link to Limbo. */
public final class LivingReturnFallback {
    private LivingReturnFallback(){}
    public static boolean returnWithoutLimbo(Entity entity) {
        if(!(entity instanceof ServerPlayer player))return false;
        var previous=DimensionalRegistry.getRiftRegistry().getPrivatePocketExit(player.getUUID());
        if(previous!=null && player.server.getLevel(previous.world)!=null) {
            var world=player.server.getLevel(previous.world);
            var safe=SafeLanding.nearConnection(player,world,previous.pos,null);
            if(safe.isPresent()) {
                var p=safe.get();player.stopRiding();player.teleportTo(world,p.x()+0.5,p.y(),p.z()+0.5,player.getYRot(),player.getXRot());
                player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;player.setPortalCooldown();
                return player.serverLevel()==world;
            }
        }
        player.displayClientMessage(Component.literal("生活空间的返回连接已失效，且附近没有安全落点。请修复连接；本次不会进入边狱。"),false);
        return false;
    }
}
