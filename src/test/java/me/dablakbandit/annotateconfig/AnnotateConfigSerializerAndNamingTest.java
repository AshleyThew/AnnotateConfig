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
import static org.junit.jupiter.api.Assertions.assertNull;

class AnnotateConfigSerializerAndNamingTest {
    @TempDir
    Path tempDir;

    @Test
    void namingStrategyHandlesSnakeCaseNullAndBlank() {
        assertEquals("value_name", NamingStrategy.LOWER_SNAKE_CASE.translate("valueName"));
        assertNull(NamingStrategy.LOWER_KEBAB_CASE.translate(null));
        assertEquals(" ", NamingStrategy.LOWER_DOT_CASE.translate(" "));
    }

    @Test
    void serializerNormalizationHandlesNestedEnumAndCharValues() throws IOException {
        FancyConfig.reset();
        Path file = tempDir.resolve("fancy.yml");

        AnnotateConfig.builder(FancyConfig.class, file)
            .serializer(Fancy.class, new FancySerializer())
            .build()
            .save();

        Map<String, Object> yaml = TestYaml.readMap(file);
        assertEquals("ON", TestYaml.get(yaml, "fancy.state"));
        assertEquals("Q", TestYaml.get(yaml, "fancy.marker"));
        @SuppressWarnings("unchecked")
        List<Object> meta = (List<Object>) TestYaml.get(yaml, "fancy.meta");
        assertEquals(List.of("ON", "Q"), meta);

        Files.writeString(file, String.join("\n",
            "fancy:",
            "  state: 'OFF'",
            "  marker: R",
            "  meta:",
            "    - 'OFF'",
            "    - R",
            ""
        ));

        AnnotateConfig.builder(FancyConfig.class, file)
            .serializer(Fancy.class, new FancySerializer())
            .build()
            .load();

        assertEquals(new Fancy(State.OFF, 'R', List.of(State.OFF, 'R')), FancyConfig.fancy);
    }

    @Test
    void explicitNullPathBindsToWrapperNull() throws IOException {
        NullConfig.reset();
        Path file = tempDir.resolve("null.yml");
        Files.writeString(file, String.join("\n",
            "value: null",
            ""
        ));

        ConfigHandle.of(NullConfig.class, file).load();
        assertNull(NullConfig.value);
    }

    @ConfigRoot
    static final class FancyConfig {
        static Fancy fancy = new Fancy(State.ON, 'Q', new ArrayList<>(List.of(State.ON, 'Q')));

        static void reset() {
            fancy = new Fancy(State.ON, 'Q', new ArrayList<>(List.of(State.ON, 'Q')));
        }
    }

    @ConfigRoot
    static final class NullConfig {
        static Integer value = 1;

        static void reset() {
            value = 1;
        }
    }

    record Fancy(State state, char marker, List<Object> meta) {
    }

    enum State {
        ON,
        OFF
    }

    static final class FancySerializer implements ConfigSerializer<Fancy> {
        @Override
        public Object serialize(Fancy value) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("state", value.state());
            map.put("marker", value.marker());
            map.put("meta", value.meta());
            return map;
        }

        @Override
        public Fancy deserialize(Object raw) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) raw;
            @SuppressWarnings("unchecked")
            List<Object> meta = (List<Object>) map.get("meta");
            List<Object> converted = new ArrayList<>();
            for (Object item : meta) {
                if (item instanceof String text && ("ON".equals(text) || "OFF".equals(text))) {
                    converted.add(State.valueOf(text));
                } else if (item instanceof String text && !text.isEmpty()) {
                    converted.add(text.charAt(0));
                } else {
                    converted.add(item);
                }
            }
            return new Fancy(
                State.valueOf(String.valueOf(map.get("state"))),
                String.valueOf(map.get("marker")).charAt(0),
                converted
            );
        }
    }
}
