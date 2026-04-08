package me.dablakbandit.annotateconfig.internal;

import me.dablakbandit.annotateconfig.ConfigHandle;
import me.dablakbandit.annotateconfig.annotation.ConfigMigrate;
import me.dablakbandit.annotateconfig.annotation.ConfigRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigBinderEdgeTest {
    @Test
    void loadWithMissingMigrationPathsFallsBackToCurrentValue() throws IOException {
        BinderMigrationConfig.reset();
        Path file = Files.createTempFile("annotate-config-binder-miss", ".yml");
        try {
            Files.writeString(file, "\n");
            ConfigHandle.of(BinderMigrationConfig.class, file).load();

            assertEquals(7, BinderMigrationConfig.value);
            var yaml = YamlSupport.loadMap(Files.readString(file));
            assertEquals(7, YamlSupport.getValue(yaml, "value"));
            assertFalse(YamlSupport.containsPath(yaml, "legacy.first"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void saveSupportsPathsWithoutParentDirectory() throws IOException {
        BinderSimpleConfig.reset();
        Path file = Path.of("annotate-config-no-parent.yml");
        try {
            ConfigHandle.of(BinderSimpleConfig.class, file).save();
            assertTrue(Files.exists(file));
            var yaml = YamlSupport.loadMap(Files.readString(file));
            assertEquals(5, YamlSupport.getValue(yaml, "number"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @ConfigRoot
    static final class BinderMigrationConfig {
        @ConfigMigrate({ "legacy.first", "legacy.second" })
        static int value = 7;

        static void reset() {
            value = 7;
        }
    }

    @ConfigRoot
    static final class BinderSimpleConfig {
        static int number = 5;

        static void reset() {
            number = 5;
        }
    }
}
