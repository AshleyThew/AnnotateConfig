package me.dablakbandit.annotateconfig;

import me.dablakbandit.annotateconfig.annotation.ConfigRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A key written with no value loads as null. Primitive fields cannot hold that, so binding it
 * would throw and abort the whole load - one blank line in a config taking the rest of it down.
 */
class BlankValueTest {

    @ConfigRoot
    public static class Config {
        public int retries = 5;
        public long timeout = 1000L;
        public double ratio = 0.5d;
        public boolean enabled = true;
        public char marker = 'x';
        public String name = "default";
    }

    @Test
    void blankPrimitivesKeepTheirDefaults(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("blank.yml");
        Files.write(file, ("retries:\ntimeout:\nratio:\nenabled:\nmarker:\n").getBytes(StandardCharsets.UTF_8));

        Config config = new Config();
        ConfigHandle.of(config, file).load();

        assertEquals(5, config.retries);
        assertEquals(1000L, config.timeout);
        assertEquals(0.5d, config.ratio);
        assertTrue(config.enabled);
        assertEquals('x', config.marker);
    }

    @Test
    void blankObjectFieldsStillBindToNull(@TempDir Path dir) throws Exception {
        // Only primitives are protected: a null String is a value a caller may legitimately want,
        // for example to mean "this message is switched off".
        Path file = dir.resolve("blank-string.yml");
        Files.write(file, "name:\n".getBytes(StandardCharsets.UTF_8));

        Config config = new Config();
        ConfigHandle.of(config, file).load();

        assertNull(config.name);
    }

    @Test
    void presentValuesStillWin(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("values.yml");
        Files.write(file, ("retries: 9\nenabled: false\nname: set\n").getBytes(StandardCharsets.UTF_8));

        Config config = new Config();
        ConfigHandle.of(config, file).load();

        assertEquals(9, config.retries);
        assertEquals(false, config.enabled);
        assertEquals("set", config.name);
    }

}
