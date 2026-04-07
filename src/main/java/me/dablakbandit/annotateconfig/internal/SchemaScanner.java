package me.dablakbandit.annotateconfig.internal;

import me.dablakbandit.annotateconfig.NamingStrategy;
import me.dablakbandit.annotateconfig.annotation.ConfigComment;
import me.dablakbandit.annotateconfig.annotation.ConfigIgnore;
import me.dablakbandit.annotateconfig.annotation.ConfigKey;
import me.dablakbandit.annotateconfig.annotation.ConfigMigrate;
import me.dablakbandit.annotateconfig.annotation.ConfigPath;
import me.dablakbandit.annotateconfig.annotation.ConfigRoot;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SchemaScanner {
    private SchemaScanner() {
    }

    public static ConfigSchema scan(
        Class<?> rootType,
        NamingStrategy namingStrategy,
        boolean preserveUnknownFields,
        SerializerRegistry serializerRegistry
    ) {
        ConfigRoot root = rootType.getAnnotation(ConfigRoot.class);
        List<String> header = root == null ? List.of() : List.of(root.header());
        Map<String, List<String>> comments = new LinkedHashMap<>();
        List<BoundField> fields = new ArrayList<>();
        scanType(rootType, "", namingStrategy, comments, fields, true);
        return new ConfigSchema(rootType, header, comments, fields, preserveUnknownFields, serializerRegistry);
    }

    private static void scanType(
        Class<?> type,
        String prefix,
        NamingStrategy namingStrategy,
        Map<String, List<String>> comments,
        List<BoundField> fields,
        boolean root
    ) {
        if (!root && type.isAnnotationPresent(ConfigIgnore.class)) {
            return;
        }

        String resolvedPrefix = prefix;
        if (!root) {
            String sectionKey = resolveSectionKey(type, namingStrategy);
            resolvedPrefix = prefix.isEmpty() ? sectionKey : prefix + "." + sectionKey;
            addComment(comments, resolvedPrefix, type.getAnnotation(ConfigComment.class));
        }
        final String scopedPrefix = resolvedPrefix;

        Arrays.stream(type.getDeclaredFields())
            .filter(field -> Modifier.isStatic(field.getModifiers()))
            .filter(field -> !field.isSynthetic())
            .filter(field -> !field.isAnnotationPresent(ConfigIgnore.class))
            .forEach(field -> scanField(field, scopedPrefix, namingStrategy, comments, fields));

        Arrays.stream(type.getDeclaredClasses())
            .filter(nested -> Modifier.isStatic(nested.getModifiers()))
            .filter(nested -> !nested.isSynthetic())
            .forEach(nested -> scanType(nested, scopedPrefix, namingStrategy, comments, fields, false));
    }

    private static void scanField(
        Field field,
        String prefix,
        NamingStrategy namingStrategy,
        Map<String, List<String>> comments,
        List<BoundField> fields
    ) {
        if (Modifier.isFinal(field.getModifiers())) {
            throw new IllegalArgumentException("Config fields must not be final: " + field.getDeclaringClass().getName() + "." + field.getName());
        }

        field.setAccessible(true);
        String path = resolveFieldPath(field, prefix, namingStrategy);
        addComment(comments, path, field.getAnnotation(ConfigComment.class));
        List<String> migrationPaths = resolveMigrationPaths(field, prefix);
        fields.add(new BoundField(field, path, migrationPaths));
    }

    private static String resolveSectionKey(Class<?> type, NamingStrategy namingStrategy) {
        ConfigKey key = type.getAnnotation(ConfigKey.class);
        return key != null ? key.value() : namingStrategy.translate(type.getSimpleName());
    }

    private static String resolveFieldPath(Field field, String prefix, NamingStrategy namingStrategy) {
        ConfigPath configPath = field.getAnnotation(ConfigPath.class);
        if (configPath != null) {
            return configPath.value();
        }
        ConfigKey key = field.getAnnotation(ConfigKey.class);
        String leaf = key != null ? key.value() : namingStrategy.translate(field.getName());
        return prefix.isEmpty() ? leaf : prefix + "." + leaf;
    }

    private static List<String> resolveMigrationPaths(Field field, String prefix) {
        ConfigMigrate migrate = field.getAnnotation(ConfigMigrate.class);
        if (migrate == null) {
            return List.of();
        }
        List<String> paths = new ArrayList<>(migrate.value().length);
        for (String value : migrate.value()) {
            paths.add(value.contains(".") || prefix.isEmpty() ? value : prefix + "." + value);
        }
        return List.copyOf(paths);
    }

    private static void addComment(Map<String, List<String>> comments, String path, ConfigComment annotation) {
        if (annotation == null || path == null || path.isBlank()) {
            return;
        }
        comments.put(path, List.of(annotation.value()));
    }
}
