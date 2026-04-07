package me.dablakbandit.annotateconfig.internal;

import me.dablakbandit.annotateconfig.ConfigSerializer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TypeConverterInternalTest {
    @Test
    void convertsPrimitiveBooleanCharAndEnumValues() {
        SerializerRegistry registry = new SerializerRegistry();

        assertEquals(5, TypeConverter.convertForField("5", Integer.class, registry));
        assertEquals(6L, TypeConverter.convertForField("6", Long.class, registry));
        assertEquals(1.25d, TypeConverter.convertForField("1.25", Double.class, registry));
        assertEquals(2.5f, TypeConverter.convertForField("2.5", Float.class, registry));
        assertEquals((short) 3, TypeConverter.convertForField("3", Short.class, registry));
        assertEquals((byte) 4, TypeConverter.convertForField("4", Byte.class, registry));
        assertEquals(true, TypeConverter.convertForField("true", Boolean.class, registry));
        assertEquals(false, TypeConverter.convertForField(false, Boolean.class, registry));

        assertEquals('A', TypeConverter.convertForField("Alpha", Character.class, registry));
        assertEquals('\0', TypeConverter.convertForField("", Character.class, registry));

        assertEquals(Mode.SECOND, TypeConverter.convertForField("SECOND", Mode.class, registry));
        assertEquals("123", TypeConverter.convertForField(123, String.class, registry));
        assertNull(TypeConverter.convertForField(null, String.class, registry));
    }

    @Test
    void convertsCollectionsAndMapsAndRejectsInvalidShapes() throws Exception {
        SerializerRegistry registry = new SerializerRegistry();

        Type listType = fieldType("ints");
        Type setType = fieldType("intSet");
        Type mapType = fieldType("named");

        Object list = TypeConverter.convertForField(List.of("1", 2), listType, registry);
        assertEquals(List.of(1, 2), list);

        Object set = TypeConverter.convertForField(List.of("1", "1", "2"), setType, registry);
        assertEquals(new LinkedHashSet<>(List.of(1, 2)), set);

        Map<String, Object> rawMap = new LinkedHashMap<>();
        rawMap.put("a", "3");
        rawMap.put("b", 4);
        Object map = TypeConverter.convertForField(rawMap, mapType, registry);
        assertEquals(Map.of("a", 3, "b", 4), map);

        assertThrows(IllegalArgumentException.class, () -> TypeConverter.convertForField("oops", listType, registry));
        assertThrows(IllegalArgumentException.class, () -> TypeConverter.convertForField("oops", mapType, registry));
    }

    @Test
    void normalizesSerializerOutputEnumsCharsCollectionsAndMaps() throws Exception {
        SerializerRegistry registry = new SerializerRegistry();
        registry.register(SerializedShape.class, new SerializedShapeSerializer());

        SerializedShape shape = new SerializedShape();
        shape.mode = Mode.FIRST;
        shape.marker = 'Q';
        shape.values = new ArrayList<>(List.of(Mode.SECOND, 'R'));

        Object normalized = TypeConverter.normalizeForYaml(shape, SerializedShape.class, registry);
        assertInstanceOf(Map.class, normalized);

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) normalized;
        assertEquals("FIRST", out.get("mode"));
        assertEquals("Q", out.get("marker"));
        assertEquals(List.of("SECOND", "R"), out.get("values"));

        Type enumListType = fieldType("modes");
        Object enumList = TypeConverter.normalizeForYaml(List.of(Mode.FIRST, Mode.SECOND), enumListType, registry);
        assertEquals(List.of("FIRST", "SECOND"), enumList);

        Type mapType = fieldType("named");
        Map<String, Integer> named = new LinkedHashMap<>();
        named.put("x", 1);
        named.put("y", 2);
        assertEquals(named, TypeConverter.normalizeForYaml(named, mapType, registry));

        assertNull(TypeConverter.normalizeForYaml(null, mapType, registry));

        Object rawListNormalization = TypeConverter.normalizeForYaml(List.of(Mode.FIRST), List.class, registry);
        assertEquals(List.of("FIRST"), rawListNormalization);

        Map<String, Object> rawMapValue = new LinkedHashMap<>();
        rawMapValue.put("k", Mode.SECOND);
        Object rawMapNormalization = TypeConverter.normalizeForYaml(rawMapValue, Map.class, registry);
        assertEquals(Map.of("k", "SECOND"), rawMapNormalization);
    }

    @Test
    void fallsBackForUnhandledTypeShapesAndClasses() {
        SerializerRegistry registry = new SerializerRegistry();

        ParameterizedType weirdType = new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return new Type[]{String.class};
            }

            @Override
            public Type getRawType() {
                return new Type() {
                };
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        };

        Object sentinel = new Object();
        assertEquals(sentinel, TypeConverter.convertForField(sentinel, weirdType, registry));
        assertEquals(123, TypeConverter.convertForField(123, UnhandledType.class, registry));
    }

    private static Type fieldType(String name) throws Exception {
        Field field = GenericTypes.class.getDeclaredField(name);
        return field.getGenericType();
    }

    static final class GenericTypes {
        List<Integer> ints;
        Set<Integer> intSet;
        List<Mode> modes;
        Map<String, Integer> named;
    }

    static final class SerializedShape {
        Mode mode;
        char marker;
        List<Object> values;
    }

    static final class UnhandledType {
    }

    static final class SerializedShapeSerializer implements ConfigSerializer<SerializedShape> {
        @Override
        public Object serialize(SerializedShape value) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("mode", value.mode);
            out.put("marker", value.marker);
            out.put("values", value.values);
            return out;
        }

        @Override
        public SerializedShape deserialize(Object raw) {
            throw new UnsupportedOperationException("not needed for this test");
        }
    }

    enum Mode {
        FIRST,
        SECOND
    }
}
