package me.dablakbandit.annotateconfig;

import me.dablakbandit.annotateconfig.annotation.ConfigRoot;
import me.dablakbandit.annotateconfig.internal.ConfigSchema;
import me.dablakbandit.annotateconfig.internal.SchemaScanner;
import me.dablakbandit.annotateconfig.internal.SerializerRegistry;

import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public final class AnnotateConfig {
    private AnnotateConfig() {
    }

    public static Builder builder(Class<?> rootType, Path file) {
        return new Builder(rootType, null, file);
    }

    public static Builder builder(Object rootInstance, Path file) {
        Object resolvedRootInstance = requireNonNull(rootInstance, "rootInstance");
        return new Builder(resolvedRootInstance.getClass(), resolvedRootInstance, file);
    }

    public static final class Builder {
        private final Class<?> rootType;
        private final Object rootInstance;
        private final Path file;
        private NamingStrategy namingStrategy;
        private Boolean preserveUnknownFields;
        private final SerializerRegistry serializerRegistry = new SerializerRegistry();

        private Builder(Class<?> rootType, Object rootInstance, Path file) {
            this.rootType = rootType;
            this.rootInstance = rootInstance;
            this.file = file;
        }

        public Builder namingStrategy(NamingStrategy namingStrategy) {
            this.namingStrategy = namingStrategy;
            return this;
        }

        public Builder preserveUnknownFields(boolean preserveUnknownFields) {
            this.preserveUnknownFields = preserveUnknownFields;
            return this;
        }

        public <T> Builder serializer(Class<T> type, ConfigSerializer<? super T> serializer) {
            serializerRegistry.register(type, serializer);
            return this;
        }

        public ConfigHandle build() {
            ConfigRoot configRoot = rootType.getAnnotation(ConfigRoot.class);
            NamingStrategy resolvedNamingStrategy = namingStrategy != null
                    ? namingStrategy
                    : configRoot != null ? configRoot.naming() : NamingStrategy.LOWER_KEBAB_CASE;
            boolean resolvedPreserveUnknownFields = preserveUnknownFields != null
                    ? preserveUnknownFields
                    : configRoot == null || configRoot.preserveUnknownFields();
            ConfigSchema schema = SchemaScanner.scan(rootType, resolvedNamingStrategy, resolvedPreserveUnknownFields,
                    serializerRegistry.copy(), rootInstance);
            return new ConfigHandle(file, schema);
        }
    }
}
