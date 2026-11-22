package dev.expedition.bridge.portal;

import dev.expedition.bridge.domain.PortalRecord;
import dev.expedition.bridge.storage.ExpeditionSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.dimdev.dimdoors.block.entity.RiftBlockEntity;
import java.util.function.Predicate;

/** Observe the player's body, not a radius around a safe landing. Never loads distant chunks. */
final class ManagedTriggerClearance {
    private ManagedTriggerClearance(){}
    static boolean isClear(ServerPlayer player,ExpeditionSavedData data) {
        var world=player.serverLevel();var dimension=world.dimension().location().toString();
        return outside(player.getBoundingBox(),pos->{
            if(!world.hasChunkAt(pos))return true; // Unknown terrain cannot establish clearance.
            // Saved endpoints identify lower halves; include upper halves and single-block rifts.
            for(var root:new BlockPos[]{pos,pos.below()}) {
                var site=new PortalRecord.Site(dimension,root.getX(),root.getY(),root.getZ());
                var entrance=data.at(site);
                if(entrance!=null && entrance.phase()!=PortalRecord.Phase.CLOSED)return true;
                if(world.getBlockEntity(root) instanceof RiftBlockEntity rift
                        && rift.getDestination() instanceof ManagedExitTarget exit) {
                    var owner=data.get(exit.portalId());
                    if(owner!=null && owner.exits().contains(site))return true;
                }
            }
            return false;
        });
    }
    static boolean outside(AABB body,Predicate<BlockPos> triggerCell) {
        var min=BlockPos.containing(body.minX,body.minY,body.minZ);
        var max=BlockPos.containing(Math.nextDown(body.maxX),Math.nextDown(body.maxY),Math.nextDown(body.maxZ));
        for(var cell:BlockPos.betweenClosed(min,max))if(triggerCell.test(cell))return false;
        return true;
    }
}
