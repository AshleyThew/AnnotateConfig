package me.dablakbandit.annotateconfig.internal;

import me.dablakbandit.annotateconfig.ConfigSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SerializerRegistry {
    private final Map<Class<?>, ConfigSerializer<?>> serializers;

    public SerializerRegistry() {
        this.serializers = new LinkedHashMap<>();
    }

    private SerializerRegistry(Map<Class<?>, ConfigSerializer<?>> serializers) {
        this.serializers = serializers;
    }

    public <T> void register(Class<T> type, ConfigSerializer<? super T> serializer) {
        serializers.put(type, serializer);
    }

    public SerializerRegistry copy() {
        return new SerializerRegistry(new LinkedHashMap<>(serializers));
    }

    @SuppressWarnings("unchecked")
    public <T> ConfigSerializer<T> find(Class<T> type) {
        ConfigSerializer<?> exact = serializers.get(type);
        if (exact != null) {
            return (ConfigSerializer<T>) exact;
        }
        for (Map.Entry<Class<?>, ConfigSerializer<?>> entry : serializers.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                return (ConfigSerializer<T>) entry.getValue();
            }
        }
        return null;
    }
}
