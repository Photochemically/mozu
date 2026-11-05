package dev.expedition.bridge.storage;

import dev.expedition.bridge.domain.*;
import net.minecraft.nbt.*;
import java.util.*;
import com.google.gson.JsonParser;
import dev.expedition.bridge.content.DungeonSpecJson;

public final class PortalNbt {
    private PortalNbt() {}
    public static CompoundTag write(PortalRecord record) {
        CompoundTag tag=new CompoundTag();
        tag.putUUID("Id",record.id()); tag.putString("Kind",record.kind().name()); tag.putInt("Tier",record.tier());
        tag.put("Entrance",site(record.entrance())); tag.putLong("Seed",record.seed());
        tag.putString("Phase",record.phase().name()); tag.putString("Structure",record.structureId()); tag.putString("Failure",record.failure());
        if(record.selection()!=null) {
            var s=new CompoundTag();s.putInt("Tier",record.selection().tier());s.putString("Id",record.selection().content().id());
            s.putString("Snapshot",DungeonSpecJson.write(record.selection().content()).toString());tag.put("Selection",s);
        }
        if(record.destination()!=null) {
            var d=record.destination(); CompoundTag target=new CompoundTag();
            target.putString("Dimension",d.dimension()); target.putInt("Pocket",d.nativePocketId());
            var b=d.bounds(); target.putIntArray("Bounds",new int[]{b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()});
            target.put("Arrival",site(d.arrival())); tag.put("Destination",target);
        }
        ListTag visits=new ListTag();
        record.visits().snapshot().forEach((player,presence)->{CompoundTag v=new CompoundTag();v.putUUID("Player",player);v.putString("State",presence.name());visits.add(v);});
        tag.put("Participants",visits);
        ListTag anchors=new ListTag();record.returnAnchors().forEach((player,anchor)->{
            var a=new CompoundTag();a.putUUID("Player",player);a.put("Site",site(anchor));a.putBoolean("InLivingSpace",record.livingVisitors().contains(player));anchors.add(a);
        });tag.put("ReturnAnchors",anchors);
        ListTag exits=new ListTag();record.exits().forEach(exit->exits.add(site(exit)));tag.put("Exits",exits);
        ListTag links=new ListTag();record.internalLinks().forEach((source,target)->{
            var link=new CompoundTag();link.put("Source",site(source));link.put("Target",site(target));links.add(link);
        });tag.put("InternalLinks",links);
        tag.putBoolean("HasArrived",record.visits().enteredBefore());
        ListTag bosses=new ListTag(); record.defeatedBosses().forEach(id->bosses.add(StringTag.valueOf(id)));tag.put("DefeatedBosses",bosses);
        return tag;
    }
    public static PortalRecord read(CompoundTag tag) {
        PortalRecord.Destination destination=null;
        if(tag.contains("Destination",Tag.TAG_COMPOUND)) {
            var d=tag.getCompound("Destination");int[] b=d.getIntArray("Bounds");
            if(b.length!=6)throw new IllegalArgumentException("Invalid saved pocket bounds");
            destination=new PortalRecord.Destination(d.getString("Dimension"),d.getInt("Pocket"),new BlockBounds(b[0],b[1],b[2],b[3],b[4],b[5]),readSite(d.getCompound("Arrival")));
        }
        Map<UUID,VisitLedger.Presence> visits=new LinkedHashMap<>();
        for(Tag child:tag.getList("Participants",Tag.TAG_COMPOUND)){
            var v=(CompoundTag)child;
            if(visits.put(v.getUUID("Player"),VisitLedger.Presence.valueOf(v.getString("State")))!=null)
                throw new IllegalArgumentException("Duplicate saved participant");
        }
        Set<String> bosses=new LinkedHashSet<>();for(Tag child:tag.getList("DefeatedBosses",Tag.TAG_STRING))bosses.add(child.getAsString());
        var record=new PortalRecord(tag.getUUID("Id"),PortalKind.valueOf(tag.getString("Kind")),tag.getInt("Tier"),readSite(tag.getCompound("Entrance")),
                tag.getLong("Seed"),PortalRecord.Phase.valueOf(tag.getString("Phase")),destination,tag.getString("Structure"),new VisitLedger(visits,tag.getBoolean("HasArrived")),bosses,tag.getString("Failure"));
        if(tag.contains("Selection",Tag.TAG_COMPOUND)) {
            var s=tag.getCompound("Selection");record.restoreSelection(new DungeonCatalog.Choice(s.getInt("Tier"),
                    DungeonSpecJson.read(s.getString("Id"),JsonParser.parseString(s.getString("Snapshot")).getAsJsonObject())));
        }
        for(Tag child:tag.getList("ReturnAnchors",Tag.TAG_COMPOUND)) {
            var a=(CompoundTag)child;var player=a.getUUID("Player");
            if(record.returnAnchor(player)!=null)throw new IllegalArgumentException("Duplicate return anchor");
            record.bindReturnAnchor(player,readSite(a.getCompound("Site")));
            if(a.getBoolean("InLivingSpace"))record.livingVisit(player,true);
        }
        for(Tag child:tag.getList("Exits",Tag.TAG_COMPOUND))record.registerExit(readSite((CompoundTag)child));
        if(tag.contains("InternalLinks") && (!(tag.get("InternalLinks") instanceof ListTag links)
                || (!links.isEmpty()&&links.getElementType()!=Tag.TAG_COMPOUND)))
            throw new IllegalArgumentException("Invalid saved native links");
        for(Tag child:tag.getList("InternalLinks",Tag.TAG_COMPOUND)) {
            var link=(CompoundTag)child;record.registerInternalLink(readSite(link.getCompound("Source")),readSite(link.getCompound("Target")));
        }
        return record;
    }
    static CompoundTag site(PortalRecord.Site site) {
        var tag=new CompoundTag();tag.putString("Dimension",site.dimension());tag.putInt("X",site.x());tag.putInt("Y",site.y());tag.putInt("Z",site.z());return tag;
    }
    static PortalRecord.Site readSite(CompoundTag tag) {
        if(!tag.contains("Dimension",Tag.TAG_STRING)||tag.getString("Dimension").isBlank())throw new IllegalArgumentException("Missing saved dimension");
        return new PortalRecord.Site(tag.getString("Dimension"),tag.getInt("X"),tag.getInt("Y"),tag.getInt("Z"));
    }
}
