package dev.expedition.bridge.diagnostics;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Local diagnostic artifact only. Callers must not fail gameplay when this write fails. */
public final class WdaBuildReportWriter {
    private WdaBuildReportWriter(){}
    public static Path write(Path directory,WdaBuildReport report,long now) throws IOException {
        Files.createDirectories(directory);
        var target=directory.resolve("wda-build-"+report.portalId()+"-"+report.runId()+".json");
        if(Files.exists(target))throw new FileAlreadyExistsException(target.toString());
        var temp=Files.createTempFile(directory,".wda-build-",".tmp");
        try {
            Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(report.snapshot(now)),StandardCharsets.UTF_8);
            try {Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unavailable){Files.move(temp,target);}
            return target;
        } finally {Files.deleteIfExists(temp);}
    }
}
