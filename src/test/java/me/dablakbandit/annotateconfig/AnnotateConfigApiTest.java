package me.dablakbandit.annotateconfig;

import me.dablakbandit.annotateconfig.annotation.ConfigOptional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
                ""));
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
                ""));

        AnnotateConfig.builder(NoRootConfig.class, file)
                .preserveUnknownFields(false)
                .build()
                .load();

        assertEquals(7, NoRootConfig.myValue);
        Map<String, Object> yaml = TestYaml.readMap(file);
        assertFalse(yaml.containsKey("unknown"));
        assertEquals(7, yaml.get("my-value"));
    }

    @Test
    void supportsInstanceBackedConfigsAlongsideStaticFields() throws IOException {
        MixedConfig staticOwner = new MixedConfig();
        staticOwner.value = 2;
        MixedConfig.globalValue = 3;
        Path file = tempDir.resolve("mixed.yml");

        ConfigHandle saveHandle = ConfigHandle.of(staticOwner, file);
        saveHandle.save();

        MixedConfig loaded = new MixedConfig();
        loaded.value = 0;
        MixedConfig.globalValue = 0;
        ConfigHandle loadHandle = ConfigHandle.of(loaded, file);
        loadHandle.load();

        assertEquals(2, loaded.value);
        assertEquals(3, MixedConfig.globalValue);
        assertEquals(loaded, loadHandle.rootInstance());
        assertEquals(loaded, loadHandle.rootInstance(MixedConfig.class));
    }

    @Test
    void supportsAutoInstantiatedRootWithNestedInstanceSection() throws IOException {
        Path file = tempDir.resolve("auto-nested-instance.yml");
        Files.writeString(file, String.join("\n",
                "root-value: 8",
                "database:",
                "  host: db.internal",
                "  port: 5440",
                ""));

        ConfigHandle handle = ConfigHandle.of(AutoNestedInstanceConfig.class, file);
        handle.load();

        AutoNestedInstanceConfig root = handle.rootInstance(AutoNestedInstanceConfig.class);
        assertNotNull(root);
        assertEquals(8, root.rootValue);
        assertNotNull(root.database);
        assertEquals("db.internal", root.database.host);
        assertEquals(5440, root.database.port);

        Map<String, Object> yaml = TestYaml.readMap(file);
        assertEquals(8, TestYaml.get(yaml, "root-value"));
        assertEquals("db.internal", TestYaml.get(yaml, "database.host"));
        assertEquals(5440, TestYaml.get(yaml, "database.port"));
    }

    @Test
    void supportsProvidedRootWithNestedInstanceSection() throws IOException {
        Path file = tempDir.resolve("provided-nested-instance.yml");
        Files.writeString(file, String.join("\n",
                "root-value: 4",
                "database:",
                "  host: db.provided",
                "  port: 6543",
                ""));

        AutoNestedInstanceConfig provided = new AutoNestedInstanceConfig();
        ConfigHandle handle = ConfigHandle.of(provided, file);
        handle.load();

        assertEquals(provided, handle.rootInstance(AutoNestedInstanceConfig.class));
        assertEquals(4, provided.rootValue);
        assertNotNull(provided.database);
        assertEquals("db.provided", provided.database.host);
        assertEquals(6543, provided.database.port);

        Map<String, Object> yaml = TestYaml.readMap(file);
        assertEquals(4, TestYaml.get(yaml, "root-value"));
        assertEquals("db.provided", TestYaml.get(yaml, "database.host"));
        assertEquals(6543, TestYaml.get(yaml, "database.port"));
    }

    @Test
    void optionalInstanceValueIsOmittedWhenUnsetAndLoadsWhenPresent() throws IOException {
        Path file = tempDir.resolve("optional-instance.yml");
        OptionalInstanceConfig config = new OptionalInstanceConfig();

        ConfigHandle.of(config, file).save();

        Map<String, Object> initialYaml = TestYaml.readMap(file);
        assertFalse(initialYaml.containsKey("display-name"));
        assertEquals(2, initialYaml.get("required-value"));

        Files.writeString(file, String.join("\n",
                "display-name: welcome",
                "required-value: 6",
                ""));

        ConfigHandle.of(config, file).load();

        assertEquals("welcome", config.displayName);
        assertEquals(6, config.requiredValue);

        Map<String, Object> loadedYaml = TestYaml.readMap(file);
        assertEquals("welcome", loadedYaml.get("display-name"));
        assertEquals(6, loadedYaml.get("required-value"));
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

    static final class MixedConfig {
        static int globalValue = 1;
        int value = 1;
    }

    static final class AutoNestedInstanceConfig {
        int rootValue = 1;
        Database database;

        class Database {
            String host = "localhost";
            int port = 3306;
        }
    }

    static final class OptionalInstanceConfig {
        @ConfigOptional
        String displayName;

        int requiredValue = 2;
    }
}
