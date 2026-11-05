package dev.expedition.bridge.storage;

import dev.expedition.bridge.domain.PortalRecord;
import dev.expedition.bridge.domain.TravelLedger;
import dev.expedition.bridge.domain.NaturalEntryPlan;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

public final class ExpeditionSavedData extends SavedData {
    public static final int SCHEMA=5;
    private final Map<UUID,PortalRecord> records=new LinkedHashMap<>();
    private final Map<String,UUID> entrances=new HashMap<>();
    private final TravelLedger travel=new TravelLedger();
    private NaturalEntryPlan.Policy naturalPolicy;
    private final Set<UUID> naturalAttempts=new LinkedHashSet<>();
    private final Set<Long> pendingNaturalChunks=new LinkedHashSet<>();
    public TravelLedger travel(){return travel;}
    public NaturalEntryPlan.Policy naturalPolicy(){return naturalPolicy;}
    public NaturalEntryPlan.Policy initializeNaturalPolicy(NaturalEntryPlan.Policy configured) {
        if(naturalPolicy==null){naturalPolicy=Objects.requireNonNull(configured);setDirty();}return naturalPolicy;
    }
    public boolean naturalAttempted(UUID id){return naturalAttempts.contains(id);}
    public void recordNaturalAttempt(UUID id){if(naturalAttempts.add(Objects.requireNonNull(id)))setDirty();}
    public boolean naturalPending(long chunk){return pendingNaturalChunks.contains(chunk);}
    public void naturalPending(long chunk,boolean pending) {
        if(pending?pendingNaturalChunks.add(chunk):pendingNaturalChunks.remove(chunk))setDirty();
    }

    public static ExpeditionSavedData get(MinecraftServer server) {
        if(!server.isSameThread())throw new IllegalStateException("Portal state accessed off the server thread");
        return server.overworld().getDataStorage().computeIfAbsent(ExpeditionSavedData::load,ExpeditionSavedData::new,"expedition_bridge");
    }
    public static ExpeditionSavedData load(CompoundTag tag) {
        int schema=tag.getInt("Schema");
        if(schema<1 || schema>SCHEMA)throw new IllegalStateException("Unsupported expedition save schema; refusing to discard portal state");
        var data=new ExpeditionSavedData();
        for(Tag child:tag.getList("Portals",Tag.TAG_COMPOUND)) {
            PortalRecord record=PortalNbt.read((CompoundTag)child);
            record.recoverInterruptedGeneration(); data.add(record);
        }
        if(schema>=2)for(Tag child:tag.getList("Travel",Tag.TAG_COMPOUND)) {
            var op=TravelNbt.read((CompoundTag)child);
            if(data.get(op.portal())==null)throw new IllegalArgumentException("Transit references missing portal");
            data.travel.restore(op);
        }
        if(schema>=4 && tag.contains("NaturalPolicy",Tag.TAG_COMPOUND))data.naturalPolicy=NaturalEntryNbt.read(tag.getCompound("NaturalPolicy"));
        if(schema>=4)for(Tag child:tag.getList("NaturalAttempts",Tag.TAG_COMPOUND)) {
            UUID id=((CompoundTag)child).getUUID("Id");
            if(!data.naturalAttempts.add(id))throw new IllegalArgumentException("Duplicate natural entry attempt");
        }
        if(!data.naturalAttempts.isEmpty() && data.naturalPolicy==null)throw new IllegalArgumentException("Natural attempts without pinned policy");
        if(schema>=4)for(long chunk:tag.getLongArray("PendingNaturalChunks"))data.pendingNaturalChunks.add(chunk);
        if(!data.pendingNaturalChunks.isEmpty() && data.naturalPolicy==null)throw new IllegalArgumentException("Pending natural chunks without pinned policy");
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("Schema",SCHEMA);ListTag list=new ListTag();records.values().forEach(r->list.add(PortalNbt.write(r)));tag.put("Portals",list);
        ListTag operations=new ListTag();travel.operations().forEach(op->operations.add(TravelNbt.write(op)));tag.put("Travel",operations);
        if(naturalPolicy!=null)tag.put("NaturalPolicy",NaturalEntryNbt.write(naturalPolicy));
        ListTag attempts=new ListTag();naturalAttempts.forEach(id->{var t=new CompoundTag();t.putUUID("Id",id);attempts.add(t);});
        tag.put("NaturalAttempts",attempts);tag.putLongArray("PendingNaturalChunks",pendingNaturalChunks.stream().mapToLong(Long::longValue).toArray());return tag;
    }
    public void add(PortalRecord record) {
        if(records.containsKey(record.id())||entrances.containsKey(record.entrance().key()))throw new IllegalArgumentException("Duplicate portal identity or entrance");
        records.put(record.id(),record);entrances.put(record.entrance().key(),record.id());setDirty();
    }
    public PortalRecord get(UUID id) { return records.get(id); }
    public PortalRecord at(PortalRecord.Site site) { return records.get(entrances.get(site.key())); }
    public Collection<PortalRecord> records() { return Collections.unmodifiableCollection(records.values()); }
}
