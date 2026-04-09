package me.dablakbandit.annotateconfig.internal;

import java.util.List;
import java.util.Map;

public record ConfigSchema(
        Class<?> rootType,
        Object rootInstance,
        List<String> header,
        Map<String, List<String>> comments,
        List<BoundField> fields,
        boolean preserveUnknownFields,
        SerializerRegistry serializerRegistry) {
}
