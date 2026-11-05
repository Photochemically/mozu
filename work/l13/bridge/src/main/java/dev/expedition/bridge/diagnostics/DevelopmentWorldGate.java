package dev.expedition.bridge.diagnostics;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Set;

/** A per-save, external opt-in. Never created by commands or shipped as an active runtime resource. */
public final class DevelopmentWorldGate {
    public static final String FILE="expedition-development-world.json";
    public static final String PURPOSE="l13_disposable_acceptance";
    private DevelopmentWorldGate(){}
    public static void require(Path worldRoot) throws IOException {
        var marker=worldRoot.resolve(FILE);
        if(!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS) || Files.size(marker)>4096)
            throw new IllegalStateException("仅限显式标记的可丢弃验收存档；缺少有效的 "+FILE);
        final String text;
        try(var input=Files.newInputStream(marker)) {
            var bytes=input.readNBytes(4097);
            if(bytes.length>4096)throw new IllegalArgumentException("Development marker too large");
            text=new String(bytes,StandardCharsets.UTF_8);
        }
        var json=JsonParser.parseString(text).getAsJsonObject();
        if(!json.keySet().equals(Set.of("schema","purpose","allow_test_entrances"))
                || !json.get("schema").isJsonPrimitive() || !json.getAsJsonPrimitive("schema").isNumber()
                || json.get("schema").getAsBigDecimal().intValueExact()!=1
                || !json.get("purpose").isJsonPrimitive() || !json.getAsJsonPrimitive("purpose").isString()
                || !PURPOSE.equals(json.get("purpose").getAsString())
                || !json.get("allow_test_entrances").isJsonPrimitive() || !json.getAsJsonPrimitive("allow_test_entrances").isBoolean()
                || !json.get("allow_test_entrances").getAsBoolean())
            throw new IllegalArgumentException("Invalid explicit development-world opt-in");
    }
}
