package me.dablakbandit.annotateconfig;

import me.dablakbandit.annotateconfig.annotation.ConfigRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SnakeYAML writes a string containing a non-printable character as !!binary and reads it back
 * as a byte[]. Binding that to a String field must give the text back, not "[B@1b6d3586".
 */
class BinaryStringTest {

    @ConfigRoot
    public static class Config {
        public String message = "plain";
    }

    @Test
    void controlCharacterSurvivesSaveAndLoad(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("binary.yml");
        // U+0090 is a C1 control character, which is what makes SnakeYAML choose !!binary.
        String withControl = "before" + (char) 0x90 + "after " + (char) 0x3010 + "bracketed" + (char) 0x3011;

        Config out = new Config();
        out.message = withControl;
        ConfigHandle.of(out, file).save();
        assertTrue(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains("!!binary"),
                "precondition: SnakeYAML chose !!binary for this value");

        Config in = new Config();
        ConfigHandle.of(in, file).load();

        assertEquals(withControl, in.message);
    }

    @Test
    void binaryWrittenByHandStillDecodes(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("hand.yml");
        // base64 of the UTF-8 bytes for "caf" followed by e-acute
        Files.write(file, "message: !!binary Y2Fmw6k=\n".getBytes(StandardCharsets.UTF_8));

        Config in = new Config();
        ConfigHandle.of(in, file).load();

        assertEquals("caf" + (char) 0xE9, in.message);
    }

}
