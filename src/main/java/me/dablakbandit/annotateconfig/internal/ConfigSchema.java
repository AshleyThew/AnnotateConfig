package me.dablakbandit.annotateconfig.internal;

import java.util.List;
import java.util.Map;

public final class ConfigSchema {
    private final Class<?> rootType;
    private final Object rootInstance;
    private final List<String> header;
    private final Map<String, List<String>> comments;
    private final List<BoundField> fields;
    private final boolean preserveUnknownFields;
    private final SerializerRegistry serializerRegistry;

    public ConfigSchema(
            Class<?> rootType,
            Object rootInstance,
            List<String> header,
            Map<String, List<String>> comments,
            List<BoundField> fields,
            boolean preserveUnknownFields,
            SerializerRegistry serializerRegistry) {
        this.rootType = rootType;
        this.rootInstance = rootInstance;
        this.header = header;
        this.comments = comments;
        this.fields = fields;
        this.preserveUnknownFields = preserveUnknownFields;
        this.serializerRegistry = serializerRegistry;
    }

    public Class<?> rootType() {
        return rootType;
    }

    public Object rootInstance() {
        return rootInstance;
    }

    public List<String> header() {
        return header;
    }

    public Map<String, List<String>> comments() {
        return comments;
    }

    public List<BoundField> fields() {
        return fields;
    }

    public boolean preserveUnknownFields() {
        return preserveUnknownFields;
    }

    public SerializerRegistry serializerRegistry() {
        return serializerRegistry;
    }

    @Override
    public String toString() {
        return "ConfigSchema[rootType=" + rootType + ", rootInstance=" + rootInstance + ", header=" + header
                + ", comments=" + comments + ", fields=" + fields + ", preserveUnknownFields=" + preserveUnknownFields
                + ", serializerRegistry=" + serializerRegistry + "]";
    }
}
