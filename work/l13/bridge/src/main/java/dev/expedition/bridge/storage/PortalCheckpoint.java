package dev.expedition.bridge.storage;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Persist generation intent before allocation. Native DD identity is validated independently on reuse. */
public final class PortalCheckpoint {
    private PortalCheckpoint() {}
    public static void write(MinecraftServer server, ExpeditionSavedData data) throws IOException {
        if(!server.isSameThread())throw new IllegalStateException("Checkpoint off server thread");
        var directory=server.getWorldPath(LevelResource.ROOT).resolve("data");
        Files.createDirectories(directory);
        var target=directory.resolve("expedition_bridge.dat");
        var temporary=Files.createTempFile(directory,"expedition_bridge-",".tmp");
        try {
            var root=new CompoundTag();root.put("data",data.save(new CompoundTag()));
            root.putInt("DataVersion",SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            NbtIo.writeCompressed(root,temporary.toFile());
            // Fail before allocation if the filesystem cannot safely replace our own checkpoint.
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            data.setDirty(false);
        } finally { Files.deleteIfExists(temporary); }
    }
}
