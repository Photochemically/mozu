package dev.expedition.bridge.portal;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.storage.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import java.io.IOException;

/** Records identity before touching blocks. Makes only an overworld entrance, never a destination pocket. */
public final class NaturalEntranceFactory {
    private NaturalEntranceFactory(){}
    public static void validateBlocks() {
        for(var type:NaturalEntryPlan.Type.values())if(!(BuiltInRegistries.BLOCK.get(ResourceLocation.parse(type.block))
                instanceof org.dimdev.dimdoors.block.door.DimensionalDoorBlock))throw new IllegalStateException("Required native entrance block missing: "+type.block);
    }
    public static PortalRecord place(ServerLevel level,BlockPos pos,NaturalEntryPlan.Candidate candidate) throws IOException {
        if(pos.getX()!=candidate.x() || pos.getZ()!=candidate.z())throw new IllegalArgumentException("Position does not match natural proposal");
        var data=ExpeditionSavedData.get(level.getServer());
        if(!data.naturalAttempted(candidate.id()))throw new IllegalStateException("Natural attempt not journaled");
        var type=candidate.type();
        var portal=new PortalRecord(candidate.id(),type.kind,type.tier,
                new PortalRecord.Site("minecraft:overworld",pos.getX(),pos.getY(),pos.getZ()),candidate.contentSeed());
        return ManagedEntrancePlacement.place(level,pos,type,candidate.facing(),portal);
    }
}
