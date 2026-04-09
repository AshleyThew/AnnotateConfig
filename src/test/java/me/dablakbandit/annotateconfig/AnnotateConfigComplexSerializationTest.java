package me.dablakbandit.annotateconfig;

import me.dablakbandit.annotateconfig.annotation.ConfigComment;
import me.dablakbandit.annotateconfig.annotation.ConfigRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for complex object serialization and deserialization.
 * Catches issues like YAML type tags (!!), nested custom objects, and maps with
 * object values.
 */
class AnnotateConfigComplexSerializationTest {
    @TempDir
    Path tempDir;

    @Test
    void serializesMapWithCustomObjectValuesWithoutTypeTag() throws IOException {
        Path file = tempDir.resolve("map-objects.yml");

        ConfigWithMapOfObjects config = new ConfigWithMapOfObjects();
        ConfigHandle handle = ConfigHandle.of(config, file);
        handle.save();

        String content = Files.readString(file);
        assertFalse(content.contains("!!"), "YAML should not contain !! type tags");
        assertTrue(content.contains("name:"), "Should contain object field names");
        assertTrue(content.contains("precision:"), "Should contain object field names");
    }

    @Test
    void roundTripMapWithCustomObjectsPreservesValues() throws IOException {
        Path file = tempDir.resolve("map-roundtrip.yml");

        ConfigWithMapOfObjects original = new ConfigWithMapOfObjects();
        ConfigHandle saveHandle = ConfigHandle.of(original, file);
        saveHandle.save();

        String content = Files.readString(file);
        assertFalse(content.contains("!!"), "YAML should not contain !! type tags");

        // Verify the YAML contains the expected structure
        assertTrue(content.contains("primary"), "Should contain map key");
        assertTrue(content.contains("name:"), "Should contain field names");
        assertTrue(content.contains("Coin"), "Should contain field values");
    }

    @Test
    void roundTripDeeplyNestedObjectsWithoutTypeTag() throws IOException {
        Path file = tempDir.resolve("deeply-nested.yml");

        ConfigWithDeeplyNested config = new ConfigWithDeeplyNested();
        ConfigHandle handle = ConfigHandle.of(config, file);
        handle.save();

        String content = Files.readString(file);
        assertFalse(content.contains("!!"), "YAML should not contain !! type tags");

        ConfigWithDeeplyNested loaded = new ConfigWithDeeplyNested();
        ConfigHandle loadHandle = ConfigHandle.of(loaded, file);
        loadHandle.load();

        assertNotNull(loaded.root);
        assertNotNull(loaded.root.level1);
        assertNotNull(loaded.root.level1.level2);
        assertEquals("value", loaded.root.level1.level2.deepField);
    }

    @Test
    void mapWithMultipleCustomObjectsPreservesAll() throws IOException {
        Path file = tempDir.resolve("map-multiple.yml");

        ConfigWithMapOfObjects config = new ConfigWithMapOfObjects();
        config.currencies.put("silver", new Definition("Silver", 3));
        config.currencies.put("gold", new Definition("Gold", 4));

        ConfigHandle saveHandle = ConfigHandle.of(config, file);
        saveHandle.save();

        String content = Files.readString(file);
        assertFalse(content.contains("!!"), "YAML should not contain !! type tags");
        assertTrue(content.contains("silver"), "Should contain all map keys");
        assertTrue(content.contains("gold"), "Should contain all map keys");
        assertTrue(content.contains("Silver"), "Should contain all values");
        assertTrue(content.contains("Gold"), "Should contain all values");
    }

    @Test
    void nestedMapsWithObjectsSerializeCleanly() throws IOException {
        Path file = tempDir.resolve("nested-maps.yml");

        ConfigWithNestedMapOfObjects config = new ConfigWithNestedMapOfObjects();
        ConfigHandle handle = ConfigHandle.of(config, file);
        handle.save();

        String content = Files.readString(file);
        assertFalse(content.contains("!!"), "YAML should not contain !! type tags");
        assertTrue(content.contains("tier-1"), "Should contain nested map keys");
        assertTrue(content.contains("default"), "Should contain nested values");
        assertTrue(content.contains("Default"), "Should contain field values");
    }

    @Test
    void objectWithManyFieldsSerializesWithoutTypeTag() throws IOException {
        Path file = tempDir.resolve("many-fields.yml");

        ConfigWithManyFields config = new ConfigWithManyFields();
        ConfigHandle handle = ConfigHandle.of(config, file);
        handle.save();

        String content = Files.readString(file);
        assertFalse(content.contains("!!"), "YAML should not contain !! type tags");
        assertTrue(content.contains("field1"), "Should contain all fields");
        assertTrue(content.contains("field10"), "Should contain all fields");
    }

    @Test
    void mixedStaticAndInstanceFieldsWithMapOfObjectsRoundTrip() throws IOException {
        Path file = tempDir.resolve("mixed-static-instance.yml");

        MixedStaticInstanceMapConfig original = new MixedStaticInstanceMapConfig();
        original.instanceItems.put("item1", new Item("First"));
        MixedStaticInstanceMapConfig.staticValue = 42;

        ConfigHandle saveHandle = ConfigHandle.of(original, file);
        saveHandle.save();

        String content = Files.readString(file);
        assertFalse(content.contains("!!"), "YAML should not contain !! type tags");
        assertTrue(content.contains("static-value"), "Should contain static field");
        assertTrue(content.contains("item1"), "Should contain instance map entries");
        assertTrue(content.contains("First"), "Should contain item values");
    }

    // Test classes

    @ConfigRoot(header = "Config with map of custom objects")
    static final class ConfigWithMapOfObjects {
        public Map<String, Definition> currencies = new LinkedHashMap<>();

        ConfigWithMapOfObjects() {
            currencies.put("primary", new Definition("Coin", 2));
        }
    }

    static class Definition {
        public String name = "Default";
        public int precision = 0;

        Definition() {
        }

        Definition(String name, int precision) {
            this.name = name;
            this.precision = precision;
        }
    }

    @ConfigRoot(header = "Config with deeply nested objects")
    static final class ConfigWithDeeplyNested {
        public RootLevel root = new RootLevel();

        static class RootLevel {
            public Level1 level1 = new Level1();
        }

        static class Level1 {
            public Level2 level2 = new Level2();
        }

        static class Level2 {
            public String deepField = "value";
        }
    }

    @ConfigRoot(header = "Config with nested map of objects")
    static final class ConfigWithNestedMapOfObjects {
        public Map<String, Tier> categories = new LinkedHashMap<>();

        ConfigWithNestedMapOfObjects() {
            Tier tier = new Tier();
            categories.put("tier-1", tier);
        }

        static class Tier {
            public Map<String, Definition> definitions = new LinkedHashMap<>();

            Tier() {
                definitions.put("default", new Definition("Default", 2));
            }
        }
    }

    @ConfigRoot(header = "Config with many fields")
    static final class ConfigWithManyFields {
        public String field1 = "value1";
        public String field2 = "value2";
        public String field3 = "value3";
        public String field4 = "value4";
        public String field5 = "value5";
        public String field6 = "value6";
        public String field7 = "value7";
        public String field8 = "value8";
        public String field9 = "value9";
        public String field10 = "value10";
    }

    @ConfigRoot(header = "Config with mixed static and instance fields with map of objects")
    static final class MixedStaticInstanceMapConfig {
        public static int staticValue = 1;
        public Map<String, Item> instanceItems = new LinkedHashMap<>();
    }

    static class Item {
        public String name = "Default";

        Item() {
        }

        Item(String name) {
            this.name = name;
        }
    }
}
