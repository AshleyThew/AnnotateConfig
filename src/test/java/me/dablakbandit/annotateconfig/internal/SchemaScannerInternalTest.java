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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaScannerInternalTest {
    @Test
    void scansNestedSectionsWithNonEmptyPrefixAndRelativeMigrationExpansion() {
        ConfigSchema schema = SchemaScanner.scan(NestedScanConfig.class, NamingStrategy.LOWER_KEBAB_CASE, true,
                new SerializerRegistry(), null);

        Map<String, BoundField> byPath = schema.fields().stream().collect(Collectors.toMap(BoundField::path, it -> it));

        assertTrue(byPath.containsKey("outer.inner.value"));
        assertTrue(byPath.containsKey("outer.inner.absolute.path"));
        assertNull(byPath.get("outer.inner.value").target());
        assertEquals(
                java.util.List.of("legacy.full.path", "outer.inner.old-relative"),
                byPath.get("outer.inner.value").migrationPaths());

        assertTrue(schema.comments().containsKey("outer"));
        assertTrue(schema.comments().containsKey("outer.inner.value"));
        assertFalse(schema.comments().containsKey("   "));
    }

    @Test
    void instanceNestedTargetsAreBoundToSameRootInstance() throws ReflectiveOperationException {
        ConfigSchema schema = SchemaScanner.scan(InstanceNestedScanConfig.class, NamingStrategy.LOWER_KEBAB_CASE, true,
                new SerializerRegistry(), null);

        Map<String, BoundField> byPath = schema.fields().stream().collect(Collectors.toMap(BoundField::path, it -> it));

        Object root = schema.rootInstance();
        assertNotNull(root);
        assertSame(root, byPath.get("root-value").target());

        Object nestedTarget = byPath.get("inner.nested-value").target();
        assertNotNull(nestedTarget);

        var outerField = nestedTarget.getClass().getDeclaredField("this$0");
        outerField.setAccessible(true);
        assertSame(root, outerField.get(nestedTarget));
    }

    static final class NestedScanConfig {
        @ConfigComment("outer section")
        static final class Outer {
            @ConfigComment("inner section")
            static final class Inner {
                @ConfigComment("value")
                @ConfigMigrate({ "legacy.full.path", "old-relative" })
                static int value = 1;

                @ConfigComment("blank path ignored")
                @ConfigPath("   ")
                static int ignoredBlankPath = 2;

                @ConfigPath("outer.inner.absolute.path")
                static int absolutePath = 3;
            }
        }
    }

    static final class InstanceNestedScanConfig {
        int rootValue = 1;

        class Inner {
            int nestedValue = 2;
        }
    }
}
