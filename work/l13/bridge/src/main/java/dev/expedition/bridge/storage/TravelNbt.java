package dev.expedition.bridge.storage;

import dev.expedition.bridge.domain.*;
import net.minecraft.nbt.*;

final class TravelNbt {
    static CompoundTag write(TravelOperation op) {
        var tag=new CompoundTag();tag.putUUID("Id",op.id());tag.putUUID("Player",op.player());tag.putUUID("Portal",op.portal());
        tag.putString("Route",op.route().name());tag.putString("Outcome",op.outcome().name());tag.putString("State",op.state().name());
        tag.put("Source",PortalNbt.site(op.source()));tag.put("Anchor",PortalNbt.site(op.anchor()));tag.putLong("ReleaseAfter",op.releaseAfter());
        if(op.landing()!=null)tag.put("Landing",PortalNbt.site(op.landing()));
        return tag;
    }
    static TravelOperation read(CompoundTag tag) {
        return new TravelOperation(tag.getUUID("Id"),tag.getUUID("Player"),tag.getUUID("Portal"),TravelRoute.valueOf(tag.getString("Route")),
                PortalNbt.readSite(tag.getCompound("Source")),PortalNbt.readSite(tag.getCompound("Anchor")),
                TravelOperation.Outcome.valueOf(tag.getString("Outcome")),TravelOperation.State.valueOf(tag.getString("State")),
                tag.contains("Landing",Tag.TAG_COMPOUND)?PortalNbt.readSite(tag.getCompound("Landing")):null,tag.getLong("ReleaseAfter"));
    }
}
