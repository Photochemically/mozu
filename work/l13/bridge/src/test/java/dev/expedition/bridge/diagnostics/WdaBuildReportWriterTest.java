package dev.expedition.bridge.diagnostics;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WdaBuildReportWriterTest {
    @TempDir Path directory;
    private WdaBuildReport report(String phase) {
        var r=new WdaBuildReport(UUID.randomUUID(),"dungeons_arise:merchant_campsite",42,0);
        r.finish(phase,phase.equals("FAILED")?"test failure":null,100);return r;
    }
    @Test void terminalJsonContainsDiagnosticLimitsAndNoTemporaryFilesRemain() throws Exception {
        var r=report("READY");var file=WdaBuildReportWriter.write(directory,r,200);
        assertEquals(directory,file.getParent());var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("READY",json.get("state").getAsString());assertFalse(json.get("runtimeAcceptancePassed").getAsBoolean());
        assertTrue(json.getAsJsonArray("limitations").size()>0);
        try(var files=Files.list(directory)){assertEquals(1,files.count());}
    }
    @Test void existingReportCannotBeOverwrittenOrTurnFailureIntoSuccess() throws Exception {
        var r=report("FAILED");var file=WdaBuildReportWriter.write(directory,r,200);var original=Files.readString(file);
        assertThrows(FileAlreadyExistsException.class,()->WdaBuildReportWriter.write(directory,r,300));
        assertEquals(original,Files.readString(file));assertEquals("FAILED",r.snapshot(300).get("state").getAsString());
    }
    @Test void unusableReportDirectoryDoesNotAlterFinishedObservation() throws Exception {
        var r=report("READY");var before=r.snapshot(200);var occupied=directory.resolve("occupied");Files.writeString(occupied,"keep");
        assertThrows(IOException.class,()->WdaBuildReportWriter.write(occupied,r,300));
        assertEquals(before,r.snapshot(400));assertEquals("keep",Files.readString(occupied));
    }
}
