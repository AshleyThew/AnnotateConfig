package me.dablakbandit.annotateconfig.internal;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
        if (type instanceof Class<?> clazz) {
            return convertForClass(raw, clazz, serializerRegistry);
        }
        if (type instanceof ParameterizedType parameterizedType) {
            Type rawType = parameterizedType.getRawType();
            if (rawType instanceof Class<?> rawClass) {
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
        var serializer = serializerRegistry.find(clazz);
        if (serializer != null) {
            return serializer.deserialize(raw);
        }
        if (clazz.isInstance(raw)) {
            return raw;
        }
        if (clazz == String.class) {
            return String.valueOf(raw);
        }
        if (clazz == int.class || clazz == Integer.class) {
            return raw instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(raw));
        }
        if (clazz == long.class || clazz == Long.class) {
            return raw instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(raw));
        }
        if (clazz == double.class || clazz == Double.class) {
            return raw instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(raw));
        }
        if (clazz == float.class || clazz == Float.class) {
            return raw instanceof Number number ? number.floatValue() : Float.parseFloat(String.valueOf(raw));
        }
        if (clazz == short.class || clazz == Short.class) {
            return raw instanceof Number number ? number.shortValue() : Short.parseShort(String.valueOf(raw));
        }
        if (clazz == byte.class || clazz == Byte.class) {
            return raw instanceof Number number ? number.byteValue() : Byte.parseByte(String.valueOf(raw));
        }
        if (clazz == boolean.class || clazz == Boolean.class) {
            return raw instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(raw));
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
        if (raw instanceof Map<?, ?> map) {
            return convertMapToBean(map, clazz, serializerRegistry);
        }
        return raw;
    }

    private static Object convertMapToBean(Map<?, ?> map, Class<?> clazz, SerializerRegistry serializerRegistry) {
        try {
            java.lang.reflect.Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object instance = constructor.newInstance();
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
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
        if (!(raw instanceof Collection<?> collection)) {
            throw new IllegalArgumentException("Expected a collection but got: " + raw.getClass().getName());
        }
        Collection<Object> converted = Set.class.isAssignableFrom(rawClass) ? new LinkedHashSet<>() : new ArrayList<>();
        for (Object element : collection) {
            converted.add(convertForField(element, elementType, serializerRegistry));
        }
        return converted;
    }

    private static Object convertMap(Object raw, Type keyType, Type valueType, SerializerRegistry serializerRegistry) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected a map but got: " + raw.getClass().getName());
        }
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
        if (type instanceof Class<?> clazz) {
            var serializer = serializerRegistry.find(clazz);
            if (serializer != null) {
                @SuppressWarnings("unchecked")
                Object serialized = ((me.dablakbandit.annotateconfig.ConfigSerializer<Object>) serializer)
                        .serialize(value);
                return normalizeSerializedValue(serialized);
            }
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof Character character) {
            return String.valueOf(character);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> normalized = new ArrayList<>(collection.size());
            Type elementType = Object.class;
            if (type instanceof ParameterizedType parameterizedType) {
                elementType = parameterizedType.getActualTypeArguments()[0];
            }
            for (Object element : collection) {
                normalized.add(normalizeForYaml(element, elementType, serializerRegistry));
            }
            return normalized;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> normalized = new LinkedHashMap<>();
            Type keyType = Object.class;
            Type valueType = Object.class;
            if (type instanceof ParameterizedType parameterizedType) {
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
        if (value instanceof Collection<?> collection) {
            List<Object> normalized = new ArrayList<>(collection.size());
            for (Object element : collection) {
                normalized.add(normalizeSerializedValue(element));
            }
            return normalized;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(normalizeSerializedValue(entry.getKey()), normalizeSerializedValue(entry.getValue()));
            }
            return normalized;
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof Character character) {
            return String.valueOf(character);
        }
        return value;
    }
}
