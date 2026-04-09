package me.dablakbandit.annotateconfig.internal;

import me.dablakbandit.annotateconfig.NamingStrategy;
import me.dablakbandit.annotateconfig.annotation.ConfigComment;
import me.dablakbandit.annotateconfig.annotation.ConfigIgnore;
import me.dablakbandit.annotateconfig.annotation.ConfigKey;
import me.dablakbandit.annotateconfig.annotation.ConfigMigrate;
import me.dablakbandit.annotateconfig.annotation.ConfigOptional;
import me.dablakbandit.annotateconfig.annotation.ConfigPath;
import me.dablakbandit.annotateconfig.annotation.ConfigRoot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SchemaScanner {
    private SchemaScanner() {
    }

    public static ConfigSchema scan(
            Class<?> rootType,
            NamingStrategy namingStrategy,
            boolean preserveUnknownFields,
            SerializerRegistry serializerRegistry) {
        return scan(rootType, namingStrategy, preserveUnknownFields, serializerRegistry, null);
    }

    public static ConfigSchema scan(
            Class<?> rootType,
            NamingStrategy namingStrategy,
            boolean preserveUnknownFields,
            SerializerRegistry serializerRegistry,
            Object providedRootInstance) {
        ConfigRoot root = rootType.getAnnotation(ConfigRoot.class);
        List<String> header = root == null ? List.of() : List.of(root.header());
        Map<String, List<String>> comments = new LinkedHashMap<>();
        List<BoundField> fields = new ArrayList<>();
        Object rootInstance = resolveRootInstance(rootType, providedRootInstance);
        scanType(rootType, "", namingStrategy, comments, fields, true, rootInstance, serializerRegistry);
        return new ConfigSchema(rootType, rootInstance, header, comments, fields, preserveUnknownFields,
                serializerRegistry);
    }

    private static void scanType(
            Class<?> type,
            String prefix,
            NamingStrategy namingStrategy,
            Map<String, List<String>> comments,
            List<BoundField> fields,
            boolean root,
            Object instance,
            SerializerRegistry serializerRegistry) {
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

        Set<Class<?>> fieldBackedSectionTypes = new HashSet<>();

        Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !field.isAnnotationPresent(ConfigIgnore.class))
                .forEach(field -> {
                    if (isSectionField(field, serializerRegistry)) {
                        fieldBackedSectionTypes.add(field.getType());
                    }
                    scanField(field, instance, scopedPrefix, namingStrategy, comments, fields, serializerRegistry);
                });

        List<Class<?>> nestedTypes = new ArrayList<>(Arrays.asList(type.getDeclaredClasses()));
        Collections.reverse(nestedTypes);
        nestedTypes.stream()
            .filter(nested -> !nested.isSynthetic())
            .filter(nested -> !nested.isAnnotationPresent(ConfigIgnore.class))
            .filter(nested -> !fieldBackedSectionTypes.contains(nested))
            .forEach(nested -> scanType(
                nested,
                scopedPrefix,
                namingStrategy,
                comments,
                fields,
                false,
                resolveNestedInstance(nested, instance),
                serializerRegistry));
    }

    private static void scanField(
            Field field,
            Object ownerInstance,
            String prefix,
            NamingStrategy namingStrategy,
            Map<String, List<String>> comments,
            List<BoundField> fields,
            SerializerRegistry serializerRegistry) {
        if (Modifier.isFinal(field.getModifiers())) {
            throw new IllegalArgumentException(
                    "Config fields must not be final: " + field.getDeclaringClass().getName() + "." + field.getName());
        }

        field.setAccessible(true);
        String path = resolveFieldPath(field, prefix, namingStrategy);
        addComment(comments, path, field.getAnnotation(ConfigComment.class));

        if (isSectionField(field, serializerRegistry)) {
            Object sectionInstance = resolveSectionFieldInstance(field, ownerInstance);
            scanType(field.getType(), path, namingStrategy, comments, fields, true, sectionInstance,
                    serializerRegistry);
            return;
        }

        List<String> migrationPaths = resolveMigrationPaths(field, prefix);
        Object target = resolveFieldTarget(field, ownerInstance);
        fields.add(
                new BoundField(field, path, migrationPaths, target, field.isAnnotationPresent(ConfigOptional.class)));
    }

    private static boolean isSectionField(Field field, SerializerRegistry serializerRegistry) {
        Class<?> fieldType = field.getType();
        if (fieldType.isPrimitive() || fieldType.isEnum()) {
            return false;
        }
        if (fieldType.isArray() || fieldType == String.class || Number.class.isAssignableFrom(fieldType)
                || fieldType == Boolean.class || fieldType == Character.class) {
            return false;
        }
        if (java.util.Collection.class.isAssignableFrom(fieldType) || java.util.Map.class.isAssignableFrom(fieldType)) {
            return false;
        }
        return serializerRegistry.find(fieldType) == null;
    }

    private static Object resolveSectionFieldInstance(Field field, Object ownerInstance) {
        Object target = resolveFieldTarget(field, ownerInstance);
        try {
            Object value = field.get(target);
            if (value != null) {
                return value;
            }

            Object instantiated = instantiateType(field.getType(), ownerInstance);
            field.set(target, instantiated);
            return instantiated;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to access config section field: " + field, exception);
        }
    }

    private static Object resolveRootInstance(Class<?> rootType, Object providedRootInstance) {
        if (providedRootInstance != null) {
            if (!rootType.isInstance(providedRootInstance)) {
                throw new IllegalArgumentException("Provided root instance is not of type " + rootType.getName());
            }
            return providedRootInstance;
        }
        return requiresInstanceBinding(rootType) ? instantiateType(rootType, null) : null;
    }

    private static Object resolveNestedInstance(Class<?> nestedType, Object enclosingInstance) {
        if (!requiresInstanceBinding(nestedType)) {
            return null;
        }
        if (Modifier.isStatic(nestedType.getModifiers())) {
            return instantiateType(nestedType, null);
        }
        if (enclosingInstance == null) {
            throw new IllegalArgumentException(
                    "Non-static config section requires an enclosing instance: " + nestedType.getName());
        }
        return instantiateType(nestedType, enclosingInstance);
    }

    private static Object resolveFieldTarget(Field field, Object ownerInstance) {
        if (Modifier.isStatic(field.getModifiers())) {
            return null;
        }
        if (ownerInstance == null) {
            throw new IllegalArgumentException("Config field requires an instance: "
                    + field.getDeclaringClass().getName() + "." + field.getName());
        }
        return ownerInstance;
    }

    private static boolean requiresInstanceBinding(Class<?> type) {
        boolean hasInstanceFields = Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !field.isAnnotationPresent(ConfigIgnore.class))
                .anyMatch(field -> !Modifier.isStatic(field.getModifiers()));
        if (hasInstanceFields) {
            return true;
        }
        return Arrays.stream(type.getDeclaredClasses())
                .filter(nested -> !nested.isSynthetic())
                .anyMatch(SchemaScanner::requiresInstanceBinding);
    }

    private static Object instantiateType(Class<?> type, Object enclosingInstance) {
        try {
            Constructor<?> constructor;
            if (!Modifier.isStatic(type.getModifiers()) && type.getEnclosingClass() != null) {
                if (enclosingInstance == null) {
                    throw new IllegalArgumentException(
                            "Cannot instantiate non-static type without enclosing instance: " + type.getName());
                }
                constructor = type.getDeclaredConstructor(type.getEnclosingClass());
                constructor.setAccessible(true);
                return constructor.newInstance(enclosingInstance);
            }

            constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Unable to instantiate config type: " + type.getName(), exception);
        }
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
