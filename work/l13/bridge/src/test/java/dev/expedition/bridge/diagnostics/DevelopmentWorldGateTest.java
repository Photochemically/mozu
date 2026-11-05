package dev.expedition.bridge.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class DevelopmentWorldGateTest {
    @TempDir Path world;
    private static final String VALID="{\"schema\":1,\"purpose\":\"l13_disposable_acceptance\",\"allow_test_entrances\":true}";
    @Test void absentOrInvalidMarkerCannotEnableMutatingCommands() throws Exception {
        assertThrows(IllegalStateException.class,()->DevelopmentWorldGate.require(world));
        assertFalse(Files.exists(world.resolve(DevelopmentWorldGate.FILE)));
        for(String value:new String[]{"{}","null",VALID.replace(":true",":false"),VALID.replace(":true",":\"true\""),
                VALID.replace(":1,",":1.5,"),VALID.replace("l13_disposable_acceptance","production")," ".repeat(4097)}) {
            Files.writeString(world.resolve(DevelopmentWorldGate.FILE),value);
            assertThrows(Exception.class,()->DevelopmentWorldGate.require(world),value);
        }
    }
    @Test void explicitOptInIsPerWorldReadOnlyAndUsesTheDistributedTemplate() throws Exception {
        var file=world.resolve(DevelopmentWorldGate.FILE);
        Files.copy(Path.of("../test-fixtures",DevelopmentWorldGate.FILE),file);
        var original=Files.readAllBytes(file);
        DevelopmentWorldGate.require(world);DevelopmentWorldGate.require(world);
        assertArrayEquals(original,Files.readAllBytes(file));
        var another=Files.createDirectory(world.resolve("another-world"));
        assertThrows(IllegalStateException.class,()->DevelopmentWorldGate.require(another));
    }
}
