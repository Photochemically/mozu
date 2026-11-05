package dev.expedition.bridge.content;

import dev.expedition.bridge.domain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.dimdev.dimdoors.api.util.*;
import org.dimdev.dimdoors.pockets.*;
import org.dimdev.dimdoors.pockets.generator.SchematicGenerator;
import org.dimdev.dimdoors.pockets.virtual.reference.IdReference;
import org.dimdev.dimdoors.rift.targets.GlobalReference;
import org.dimdev.dimdoors.world.ModDimensions;
import org.dimdev.dimdoors.world.level.registry.DimensionalRegistry;
import org.dimdev.dimdoors.world.pocket.VirtualLocation;

/** One exact native schematic/generator, never a group that can secretly select a different room. */
public final class NativeRoomProvider {
    public static void validate(DungeonSpec spec) {
        if(spec.provider()!=DungeonSpec.Provider.NATIVE)throw new IllegalArgumentException("Not a native room");
        var id=ResourceLocation.parse(spec.target());var loader=PocketLoader.getInstance();
        if(!(loader.getGenerator(id) instanceof SchematicGenerator generator) || !id.equals(generator.getTemplateID())
                || loader.getTemplates().get(Path.stringPath(id))==null)
            throw new IllegalArgumentException("Native single-room template/generator missing: "+id);
        NativeGenerationGuard.validatedDefinition(id,null);
    }
    public PortalRecord.Destination generate(MinecraftServer server,PortalRecord portal,DungeonSpec spec) {
        if(!server.isSameThread() || portal.phase()!=PortalRecord.Phase.PREPARING || spec.provider()!=DungeonSpec.Provider.NATIVE)
            throw new IllegalStateException("Native room must be selected and journaled before allocation");
        validate(spec);var id=ResourceLocation.parse(spec.target());
        var world=server.getLevel(ModDimensions.DUNGEON);if(world==null)throw new IllegalStateException("Dungeon dimension unavailable");
        var e=portal.entrance();var origin=new Location(server.overworld(),new BlockPos(e.x(),e.y(),e.z()));
        var virtual=VirtualLocation.fromLocation(origin);virtual=new VirtualLocation(virtual.getWorld(),virtual.getX(),virtual.getZ(),virtual.getDepth()+1);
        var tag=new CompoundTag();tag.putString("id",id.getPath());
        var reference=new IdReference();reference.fromNbt(tag,server.getResourceManager());
        try(var guard=NativeGenerationGuard.open(server,portal,id)) {
            var pocket=reference.prepareAndPlacePocket(new PocketGenerationContext(world,virtual,new GlobalReference(origin),null));
            var entry=DimensionalRegistry.getRiftRegistry().getPocketEntrance(pocket);
            if(entry==null)throw new IllegalStateException("Native room missing entrance; reservation retained");
            return guard.finish(pocket,new PortalRecord.Site(entry.world.location().toString(),entry.pos.getX(),entry.pos.getY(),entry.pos.getZ()));
        }
    }
}
