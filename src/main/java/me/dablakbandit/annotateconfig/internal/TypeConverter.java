package me.dablakbandit.annotateconfig.internal;

import me.dablakbandit.annotateconfig.ConfigSerializer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TypeConverter {
    private TypeConverter() {
    }

    static Object convertForField(Object raw, Type type, SerializerRegistry serializerRegistry) {
        if (raw == null) {
            return null;
        }
        if (type instanceof Class<?>) {
            return convertForClass(raw, (Class<?>) type, serializerRegistry);
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type rawType = parameterizedType.getRawType();
            if (rawType instanceof Class<?>) {
                Class<?> rawClass = (Class<?>) rawType;
                if (Collection.class.isAssignableFrom(rawClass)) {
                    return convertCollection(raw, rawClass, parameterizedType.getActualTypeArguments()[0],
                            serializerRegistry);
                }
                if (Map.class.isAssignableFrom(rawClass)) {
                    return convertMap(raw, parameterizedType.getActualTypeArguments()[0],
                            parameterizedType.getActualTypeArguments()[1], serializerRegistry);
                }
            }
        }
        return raw;
    }

    private static Object convertForClass(Object raw, Class<?> clazz, SerializerRegistry serializerRegistry) {
        ConfigSerializer<?> serializer = serializerRegistry.find(clazz);
        if (serializer != null) {
            return serializer.deserialize(raw);
        }
        if (clazz.isInstance(raw)) {
            return raw;
        }
        if (clazz == String.class) {
            // SnakeYAML writes any string holding a non-printable character as !!binary, and reads
            // that back as a byte[] of its UTF-8 encoding. String.valueOf would give "[B@1b6d3586",
            // so decode it to get the original text back.
            if (raw instanceof byte[]) {
                return new String((byte[]) raw, StandardCharsets.UTF_8);
            }
            return String.valueOf(raw);
        }
        if (clazz == int.class || clazz == Integer.class) {
            return raw instanceof Number ? ((Number) raw).intValue() : Integer.parseInt(String.valueOf(raw));
        }
        if (clazz == long.class || clazz == Long.class) {
            return raw instanceof Number ? ((Number) raw).longValue() : Long.parseLong(String.valueOf(raw));
        }
        if (clazz == double.class || clazz == Double.class) {
            return raw instanceof Number ? ((Number) raw).doubleValue() : Double.parseDouble(String.valueOf(raw));
        }
        if (clazz == float.class || clazz == Float.class) {
            return raw instanceof Number ? ((Number) raw).floatValue() : Float.parseFloat(String.valueOf(raw));
        }
        if (clazz == short.class || clazz == Short.class) {
            return raw instanceof Number ? ((Number) raw).shortValue() : Short.parseShort(String.valueOf(raw));
        }
        if (clazz == byte.class || clazz == Byte.class) {
            return raw instanceof Number ? ((Number) raw).byteValue() : Byte.parseByte(String.valueOf(raw));
        }
        if (clazz == boolean.class || clazz == Boolean.class) {
            return raw instanceof Boolean ? (Boolean) raw : Boolean.parseBoolean(String.valueOf(raw));
        }
        if (clazz == char.class || clazz == Character.class) {
            String value = String.valueOf(raw);
            return value.isEmpty() ? '\0' : value.charAt(0);
        }
        if (clazz.isEnum()) {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            Object enumValue = Enum.valueOf((Class<? extends Enum>) clazz.asSubclass(Enum.class), String.valueOf(raw));
            return enumValue;
        }
        if (raw instanceof Map<?, ?>) {
            return convertMapToBean((Map<?, ?>) raw, clazz, serializerRegistry);
        }
        return raw;
    }

    private static Object convertMapToBean(Map<?, ?> map, Class<?> clazz, SerializerRegistry serializerRegistry) {
        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object instance = constructor.newInstance();
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                Object rawValue = map.get(field.getName());
                if (rawValue == null) {
                    continue;
                }
                field.setAccessible(true);
                field.set(instance, convertForField(rawValue, field.getGenericType(), serializerRegistry));
            }
            return instance;
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                    "Unable to convert map to " + clazz.getName() + ": " + e.getMessage(), e);
        }
    }

    private static Object convertCollection(Object raw, Class<?> rawClass, Type elementType,
            SerializerRegistry serializerRegistry) {
        if (!(raw instanceof Collection<?>)) {
            throw new IllegalArgumentException("Expected a collection but got: " + raw.getClass().getName());
        }
        Collection<?> collection = (Collection<?>) raw;
        Collection<Object> converted = Set.class.isAssignableFrom(rawClass) ? new LinkedHashSet<>() : new ArrayList<>();
        for (Object element : collection) {
            converted.add(convertForField(element, elementType, serializerRegistry));
        }
        return converted;
    }

    private static Object convertMap(Object raw, Type keyType, Type valueType, SerializerRegistry serializerRegistry) {
        if (!(raw instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Expected a map but got: " + raw.getClass().getName());
        }
        Map<?, ?> map = (Map<?, ?>) raw;
        Map<Object, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = convertForField(entry.getKey(), keyType, serializerRegistry);
            Object value = convertForField(entry.getValue(), valueType, serializerRegistry);
            converted.put(key, value);
        }
        return converted;
    }

    static Object normalizeForYaml(Object value, Type type, SerializerRegistry serializerRegistry) {
        if (value == null) {
            return null;
        }
        if (type instanceof Class<?>) {
            ConfigSerializer<?> serializer = serializerRegistry.find((Class<?>) type);
            if (serializer != null) {
                @SuppressWarnings("unchecked")
                Object serialized = ((ConfigSerializer<Object>) serializer).serialize(value);
                return normalizeSerializedValue(serialized);
            }
        }
        if (value instanceof Enum<?>) {
            return ((Enum<?>) value).name();
        }
        if (value instanceof Character) {
            return String.valueOf(value);
        }
        if (value instanceof Collection<?>) {
            Collection<?> collection = (Collection<?>) value;
            List<Object> normalized = new ArrayList<>(collection.size());
            Type elementType = Object.class;
            if (type instanceof ParameterizedType) {
                elementType = ((ParameterizedType) type).getActualTypeArguments()[0];
            }
            for (Object element : collection) {
                normalized.add(normalizeForYaml(element, elementType, serializerRegistry));
            }
            return normalized;
        }
        if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            Map<Object, Object> normalized = new LinkedHashMap<>();
            Type keyType = Object.class;
            Type valueType = Object.class;
            if (type instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) type;
                keyType = parameterizedType.getActualTypeArguments()[0];
                valueType = parameterizedType.getActualTypeArguments()[1];
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(normalizeForYaml(entry.getKey(), keyType, serializerRegistry),
                        normalizeForYaml(entry.getValue(), valueType, serializerRegistry));
            }
            return normalized;
        }
        return value;
    }

    private static Object normalizeSerializedValue(Object value) {
        if (value instanceof Collection<?>) {
            Collection<?> collection = (Collection<?>) value;
            List<Object> normalized = new ArrayList<>(collection.size());
            for (Object element : collection) {
                normalized.add(normalizeSerializedValue(element));
            }
            return normalized;
        }
        if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            Map<Object, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(normalizeSerializedValue(entry.getKey()), normalizeSerializedValue(entry.getValue()));
            }
            return normalized;
        }
        if (value instanceof Enum<?>) {
            return ((Enum<?>) value).name();
        }
        if (value instanceof Character) {
            return String.valueOf(value);
        }
        return value;
    }
}
