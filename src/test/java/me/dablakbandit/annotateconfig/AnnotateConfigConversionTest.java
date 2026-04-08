package me.dablakbandit.annotateconfig;

import me.dablakbandit.annotateconfig.annotation.ConfigRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnnotateConfigConversionTest {
    @TempDir
    Path tempDir;

    @Test
    void convertsQuotedStringsToPrimitiveAndWrapperTypes() throws IOException {
        PrimitiveConfig.reset();
        Path file = tempDir.resolve("primitive.yml");
        Files.writeString(file, String.join("\n",
                "int-value: '12'",
                "long-value: '13'",
                "double-value: '1.5'",
                "float-value: '2.5'",
                "short-value: '6'",
                "byte-value: '7'",
                "bool-value: 'true'",
                "char-value: 'Z'",
                "mode: 'SECOND'",
                ""));

        ConfigHandle.of(PrimitiveConfig.class, file).load();

        assertEquals(12, PrimitiveConfig.intValue);
        assertEquals(13L, PrimitiveConfig.longValue);
        assertEquals(1.5d, PrimitiveConfig.doubleValue);
        assertEquals(2.5f, PrimitiveConfig.floatValue);
        assertEquals((short) 6, PrimitiveConfig.shortValue);
        assertEquals((byte) 7, PrimitiveConfig.byteValue);
        assertEquals(true, PrimitiveConfig.boolValue);
        assertEquals('Z', PrimitiveConfig.charValue);
        assertEquals(Mode.SECOND, PrimitiveConfig.mode);
    }

    @Test
    void throwsWhenListTypeReceivesScalar() throws IOException {
        CollectionShapeConfig.reset();
        Path file = tempDir.resolve("list-shape.yml");
        Files.writeString(file, String.join("\n",
                "numbers: 1",
                "named: {}",
                ""));

        assertThrows(IllegalArgumentException.class, () -> ConfigHandle.of(CollectionShapeConfig.class, file).load());
    }

    @Test
    void throwsWhenMapTypeReceivesScalar() throws IOException {
        CollectionShapeConfig.reset();
        Path file = tempDir.resolve("map-shape.yml");
        Files.writeString(file, String.join("\n",
                "numbers: []",
                "named: 1",
                ""));

        assertThrows(IllegalArgumentException.class, () -> ConfigHandle.of(CollectionShapeConfig.class, file).load());
    }

    @ConfigRoot
    static final class PrimitiveConfig {
        static Integer intValue = 0;
        static Long longValue = 0L;
        static Double doubleValue = 0.0d;
        static Float floatValue = 0.0f;
        static Short shortValue = 0;
        static Byte byteValue = 0;
        static Boolean boolValue = false;
        static Character charValue = 'A';
        static Mode mode = Mode.FIRST;

        static void reset() {
            intValue = 0;
            longValue = 0L;
            doubleValue = 0.0d;
            floatValue = 0.0f;
            shortValue = 0;
            byteValue = 0;
            boolValue = false;
            charValue = 'A';
            mode = Mode.FIRST;
        }
    }

    @ConfigRoot
    static final class CollectionShapeConfig {
        static List<Integer> numbers = new ArrayList<>();
        static Map<String, Integer> named = new LinkedHashMap<>();

        static void reset() {
            numbers = new ArrayList<>();
            named = new LinkedHashMap<>();
        }
    }

    enum Mode {
        FIRST,
        SECOND
    }
}
