package me.dablakbandit.annotateconfig.internal;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigBinder {
    private ConfigBinder() {
    }

    public static void load(Path file, ConfigSchema schema) throws IOException {
        ensureParentExists(file);
        Map<String, Object> current = readYaml(file);
        bindFields(schema, current);
        write(file, schema, current);
    }

    public static void save(Path file, ConfigSchema schema) throws IOException {
        ensureParentExists(file);
        Map<String, Object> current = schema.preserveUnknownFields() ? readYaml(file) : new LinkedHashMap<>();
        write(file, schema, current);
    }

    private static void bindFields(ConfigSchema schema, Map<String, Object> current) {
        for (BoundField boundField : schema.fields()) {
            Field field = boundField.field();
            Object resolved = YamlSupport.getValue(current, boundField.path());
            if (resolved == null && !YamlSupport.containsPath(current, boundField.path())) {
                for (String migrationPath : boundField.migrationPaths()) {
                    if (YamlSupport.containsPath(current, migrationPath)) {
                        resolved = YamlSupport.removeValue(current, migrationPath);
                        break;
                    }
                }
            }

            try {
                if (resolved != null || YamlSupport.containsPath(current, boundField.path())) {
                    field.set(boundField.target(), TypeConverter.convertForField(resolved, field.getGenericType(),
                            schema.serializerRegistry()));
                }
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Unable to set config field: " + field, exception);
            }
        }
    }

    private static void write(Path file, ConfigSchema schema, Map<String, Object> base) throws IOException {
        Map<String, Object> output = schema.preserveUnknownFields() ? deepCopy(base) : new LinkedHashMap<>();

        for (BoundField boundField : schema.fields()) {
            Field field = boundField.field();
            Object value;
            try {
                value = field.get(boundField.target());
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Unable to read config field: " + field, exception);
            }
            if (boundField.optional() && value == null) {
                YamlSupport.removeValue(output, boundField.path());
                continue;
            }
            YamlSupport.setValue(output, boundField.path(),
                    TypeConverter.normalizeForYaml(value, field.getGenericType(), schema.serializerRegistry()));
        }

        String rendered = YamlSupport.dumpWithComments(output, schema.comments(), schema.header());
        Files.writeString(file, rendered, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                copy.put(entry.getKey(), deepCopy((Map<String, Object>) nested));
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    private static Map<String, Object> readYaml(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        return YamlSupport.loadMap(Files.readString(file, StandardCharsets.UTF_8));
    }

    private static void ensureParentExists(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}