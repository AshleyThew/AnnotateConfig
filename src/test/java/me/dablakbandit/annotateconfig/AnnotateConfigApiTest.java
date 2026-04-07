package me.dablakbandit.annotateconfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AnnotateConfigApiTest {
    @TempDir
    Path tempDir;

    @Test
    void exposesHandleMetadataAndReloadsFromDisk() throws IOException {
        ReloadConfig.reset();
        Path file = tempDir.resolve("reload.yml");

        ConfigHandle handle = ConfigHandle.of(ReloadConfig.class, file);
        assertEquals(file, handle.file());
        assertEquals(ReloadConfig.class, handle.rootType());

        handle.save();

        Files.writeString(file, String.join("\n",
            "count: 9",
            ""
        ));
        handle.reload();

        assertEquals(9, ReloadConfig.count);
    }

    @Test
    void builderDefaultsAndOverrideWorkWithoutConfigRootAnnotation() throws IOException {
        NoRootConfig.reset();
        Path file = tempDir.resolve("no-root.yml");
        Files.writeString(file, String.join("\n",
            "my-value: 7",
            "unknown: keep",
            ""
        ));

        AnnotateConfig.builder(NoRootConfig.class, file)
            .preserveUnknownFields(false)
            .build()
            .load();

        assertEquals(7, NoRootConfig.myValue);
        Map<String, Object> yaml = TestYaml.readMap(file);
        assertFalse(yaml.containsKey("unknown"));
        assertEquals(7, yaml.get("my-value"));
    }

    static final class ReloadConfig {
        static int count = 1;

        static void reset() {
            count = 1;
        }
    }

    static final class NoRootConfig {
        static int myValue = 1;

        static void reset() {
            myValue = 1;
        }
    }
}
