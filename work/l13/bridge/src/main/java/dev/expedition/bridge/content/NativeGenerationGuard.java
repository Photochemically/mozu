package dev.expedition.bridge.content;

import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.storage.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.dimdev.dimdoors.pockets.PocketGenerationContext;
import org.dimdev.dimdoors.pockets.PocketLoader;
import org.dimdev.dimdoors.pockets.generator.*;
import org.dimdev.dimdoors.pockets.virtual.reference.PocketGeneratorReference;
import org.dimdev.dimdoors.pockets.virtual.reference.IdReference;
import org.dimdev.dimdoors.world.ModDimensions;
import org.dimdev.dimdoors.world.pocket.PocketDirectory;
import org.dimdev.dimdoors.world.pocket.type.*;
import java.io.UncheckedIOException;

/** Active only around bridge-owned native creation; ordinary DD living-space calls pass through. */
public final class NativeGenerationGuard implements AutoCloseable {
    private static final ThreadLocal<NativeGenerationGuard> ACTIVE=new ThreadLocal<>();
    private final MinecraftServer server;
    private final PortalRecord portal;
    private final ResourceLocation template;
    private Pocket allocated;
    private BlockBounds schematicBounds;
    private NativeGenerationGuard(MinecraftServer server,PortalRecord portal,ResourceLocation template) {
        this.server=server;this.portal=portal;this.template=template;
    }
    public static NativeGenerationGuard open(MinecraftServer server,PortalRecord portal,ResourceLocation template) {
        if(!server.isSameThread() || ACTIVE.get()!=null || LazyPocketGenerator.currentlyGenerating || !LazyPocketGenerator.generationQueue.isEmpty())
            throw new IllegalStateException("Native generation is already active or has pending work");
        if(portal.phase()!=PortalRecord.Phase.PREPARING || ExpeditionSavedData.get(server).get(portal.id())!=portal)
            throw new IllegalStateException("Native generation needs a journaled preparation");
        var scope=new NativeGenerationGuard(server,portal,template);ACTIVE.set(scope);return scope;
    }
    public static PocketGenerator isolatedGenerator(PocketGeneratorReference reference,PocketGenerationContext context) {
        var original=reference.getReferencedPocketGenerator(context);var scope=ACTIVE.get();
        if(scope==null)return original;
        if(!context.world().dimension().equals(ModDimensions.DUNGEON) || !(original instanceof SchematicGenerator schematic)
                || !scope.template.equals(schematic.getTemplateID()))throw new IllegalArgumentException("Unexpected native generator or dimension");
        if(reference.getClass()!=IdReference.class)throw new IllegalArgumentException("Native route must remain a direct room reference");
        var tag=validatedDefinition(scope.template,reference);
        // The loader holds mutable generator instances, including SchematicGenerator.rifts. A fresh copy
        // keeps one instance's rift collection and shell state from leaking into another room.
        var copy=PocketGenerator.deserialize(tag.copy(),scope.server.getResourceManager());
        if(!(copy instanceof SchematicGenerator))throw new IllegalStateException("Native schematic copy unavailable");
        return copy;
    }
    public static CompoundTag validatedDefinition(ResourceLocation template,PocketGeneratorReference reference) {
        if(!template.getNamespace().equals("dimdoors"))throw new IllegalArgumentException("Native IdReference only supports the dimdoors namespace");
        var original=PocketLoader.getInstance().getGenerator(template);
        if(!(original instanceof SchematicGenerator schematic)||!template.equals(schematic.getTemplateID()))
            throw new IllegalArgumentException("Native single-room generator mismatch: "+template);
        CompoundTag ref;
        if(reference==null) {
            ref=new CompoundTag();ref.putString("type","dimdoors:id");ref.putString("id",template.getPath());
        } else {
            if(reference.getClass()!=IdReference.class)throw new IllegalArgumentException("Native route must remain a direct room reference");
            ref=(CompoundTag)reference.toNbt(new CompoundTag(),false);
            // Upstream omits the superclass type writer when serializing a reference.
            reference.getType().toNbt(ref);
        }
        var tag=(CompoundTag)original.toNbt(new CompoundTag(),false);
        NativeGeneratorPolicy.validate(tag,ref,template.getPath());return tag;
    }
    public static Pocket allocate(PocketDirectory directory,Pocket.PocketBuilder<?,?> builder) {
        var scope=ACTIVE.get();
        if(scope==null)return directory.newPocket(builder);
        if(scope.allocated!=null || !(builder instanceof LazyGenerationPocket.LazyGenerationPocketBuilder))
            throw new IllegalStateException("Managed native generation must allocate exactly one lazy room");
        var expected=builder.getExpectedSize();
        var pocket=directory.newPocket(builder);scope.allocated=pocket;
        scope.schematicBounds=NativeReservation.schematicBounds(bounds(pocket));
        var bounds=NativeReservation.envelope(bounds(pocket),expected.getX(),expected.getY(),expected.getZ());
        var world=scope.server.getLevel(ModDimensions.DUNGEON);
        if(world==null)throw new IllegalStateException("Dungeon world unavailable");
        var data=ExpeditionSavedData.get(scope.server);var dimension=pocket.getWorld().location().toString();
        NativeReservation.validate(dimension,pocket.getId(),bounds,world.getMinBuildHeight(),world.getMaxBuildHeight(),data.records());
        if(!pocket.addAddon(new PocketIdentity(scope.portal.id())))throw new IllegalStateException("Native identity could not be attached");
        // This placeholder is never used for travel. The final registered rift is resolved before GENERATING/READY.
        scope.portal.reserve(scope.portal.structureId(),new PortalRecord.Destination(dimension,pocket.getId(),bounds,
                new PortalRecord.Site(dimension,bounds.minX(),bounds.minY(),bounds.minZ())));
        data.setDirty();
        try { PortalCheckpoint.write(scope.server,data); }
        catch(java.io.IOException failure) { throw new UncheckedIOException("Native reservation checkpoint failed before template placement",failure); }
        return pocket;
    }
    public static void attach(LazyPocketGenerator generator,LazyGenerationPocket pocket) {
        var scope=ACTIVE.get();
        if(scope!=null) {
            var reserved=scope.portal.destination();var actual=bounds(pocket);
            if(pocket!=scope.allocated || reserved==null || (!actual.equals(scope.schematicBounds) && !actual.equals(reserved.bounds())))
                throw new IllegalStateException("Native modifiers changed the reviewed schematic extent");
            // DD 5.4.4 Pocket.expand discards BoundingBox.inflatedBy's return value. Correct only the
            // managed pocket's metadata, after cloning the original placement origin and before init().
            var full=reserved.bounds();
            pocket.offsetOrigin(full.minX()-actual.minX(),full.minY()-actual.minY(),full.minZ()-actual.minZ());
            pocket.setSize(full.width(),full.height(),full.depth());
            scope.verify(pocket);
        }
        generator.attachToPocket(pocket);
    }
    private void verify(Pocket pocket) {
        var reserved=portal.destination();
        if(pocket!=allocated || reserved==null || portal.phase()!=PortalRecord.Phase.RESERVED
                || !reserved.bounds().equals(bounds(pocket)) || reserved.nativePocketId()!=pocket.getId()
                || !reserved.dimension().equals(pocket.getWorld().location().toString()))
            throw new IllegalStateException("Native room changed its reserved extent before lazy placement");
    }
    public PortalRecord.Destination finish(Pocket pocket,PortalRecord.Site arrival) {
        verify(pocket);
        var result=new PortalRecord.Destination(pocket.getWorld().location().toString(),pocket.getId(),bounds(pocket),arrival);
        portal.resolveReservedArrival(result);return result;
    }
    private static BlockBounds bounds(Pocket pocket) {
        var b=pocket.getBox();return new BlockBounds(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ());
    }
    @Override public void close() {
        if(ACTIVE.get()!=this)throw new IllegalStateException("Native generation scope mismatch");
        ACTIVE.remove();
        // Upstream resets this flag only on its success path; a refused allocation must not poison living doors.
        LazyPocketGenerator.currentlyGenerating=false;
    }
}
