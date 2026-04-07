package me.dablakbandit.annotateconfig.internal;

import me.dablakbandit.annotateconfig.NamingStrategy;
import me.dablakbandit.annotateconfig.annotation.ConfigComment;
import me.dablakbandit.annotateconfig.annotation.ConfigMigrate;
import me.dablakbandit.annotateconfig.annotation.ConfigPath;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaScannerInternalTest {
    @Test
    void scansNestedSectionsWithNonEmptyPrefixAndRelativeMigrationExpansion() {
        ConfigSchema schema = SchemaScanner.scan(NestedScanConfig.class, NamingStrategy.LOWER_KEBAB_CASE, true, new SerializerRegistry());

        Map<String, BoundField> byPath = schema.fields().stream().collect(Collectors.toMap(BoundField::path, it -> it));

        assertTrue(byPath.containsKey("outer.inner.value"));
        assertTrue(byPath.containsKey("outer.inner.absolute.path"));
        assertEquals(
            java.util.List.of("legacy.full.path", "outer.inner.old-relative"),
            byPath.get("outer.inner.value").migrationPaths()
        );

        assertTrue(schema.comments().containsKey("outer"));
        assertTrue(schema.comments().containsKey("outer.inner.value"));
        assertFalse(schema.comments().containsKey("   "));
    }

    static final class NestedScanConfig {
        @ConfigComment("outer section")
        static final class Outer {
            @ConfigComment("inner section")
            static final class Inner {
                @ConfigComment("value")
                @ConfigMigrate({"legacy.full.path", "old-relative"})
                static int value = 1;

                @ConfigComment("blank path ignored")
                @ConfigPath("   ")
                static int ignoredBlankPath = 2;

                @ConfigPath("outer.inner.absolute.path")
                static int absolutePath = 3;
            }
        }
    }
}
