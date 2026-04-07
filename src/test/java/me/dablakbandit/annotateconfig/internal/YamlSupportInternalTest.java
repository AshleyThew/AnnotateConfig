package me.dablakbandit.annotateconfig.internal;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlSupportInternalTest {
    @Test
    void loadMapHandlesBlankNullAndInvalidRootShapes() {
        assertTrue(YamlSupport.loadMap(null).isEmpty());
        assertTrue(YamlSupport.loadMap("  \n").isEmpty());
        assertTrue(YamlSupport.loadMap("null\n").isEmpty());

        assertThrows(IllegalArgumentException.class, () -> YamlSupport.loadMap("- 1\n- 2\n"));
        assertThrows(IllegalArgumentException.class, () -> YamlSupport.loadMap("value\n"));
    }

    @Test
    void setGetContainsAndRemovePathCoverEdgeCases() {
        Map<String, Object> root = new LinkedHashMap<>();

        root.put("broken", 1);
        YamlSupport.setValue(root, "broken.nested.value", 9);
        assertEquals(9, YamlSupport.getValue(root, "broken.nested.value"));

        assertNull(YamlSupport.getValue(root, "broken.nested.value.more"));

        YamlSupport.setValue(root, "nullable.key", null);
        assertTrue(YamlSupport.containsPath(root, "nullable.key"));

        assertNull(YamlSupport.removeValue(root, "missing.path.value"));

        Object removed = YamlSupport.removeValue(root, "broken.nested.value");
        assertEquals(9, removed);
        assertFalse(YamlSupport.containsPath(root, "broken.nested.value"));
        assertFalse(YamlSupport.containsPath(root, "broken.nested"));
    }

    @Test
    void dumpWithCommentsAddsHeaderAndNestedCommentsAndHandlesEmptyMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, List<String>> comments = new LinkedHashMap<>();

        String headerOnly = YamlSupport.dumpWithComments(data, comments, List.of("Generated", "# RawHeader"));
        assertTrue(headerOnly.contains("# Generated"));
        assertTrue(headerOnly.contains("# RawHeader"));

        data.put("root", Map.of("child", 5));
        comments.put("root", List.of("Root comment"));
        comments.put("root.child", List.of("# Child comment"));

        String rendered = YamlSupport.dumpWithComments(data, comments, List.of());
        assertTrue(rendered.contains("# Root comment"));
        assertTrue(rendered.contains("# Child comment"));
        assertTrue(rendered.contains("root:"));
        assertTrue(rendered.contains("child: 5"));
    }

    @Test
    void dumpWithCommentsHandlesMultilineScalarContinuationLines() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "line1\nline2");

        String rendered = YamlSupport.dumpWithComments(data, Map.of("message", List.of("block")), List.of());

        assertTrue(rendered.contains("# block"));
        assertTrue(rendered.contains("message:"));
        assertTrue(rendered.contains("line2"));
    }
}
