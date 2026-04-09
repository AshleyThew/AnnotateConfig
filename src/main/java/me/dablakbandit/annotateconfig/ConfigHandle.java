package me.dablakbandit.annotateconfig;

import me.dablakbandit.annotateconfig.internal.ConfigBinder;
import me.dablakbandit.annotateconfig.internal.ConfigSchema;
import me.dablakbandit.annotateconfig.internal.SchemaScanner;

import java.io.IOException;
import java.nio.file.Path;

public final class ConfigHandle {
    private final Path file;
    private final ConfigSchema schema;

    ConfigHandle(Path file, ConfigSchema schema) {
        this.file = file;
        this.schema = schema;
    }

    public Path file() {
        return file;
    }

    public Class<?> rootType() {
        return schema.rootType();
    }

    public Object rootInstance() {
        return schema.rootInstance();
    }

    public <T> T rootInstance(Class<T> type) {
        return type.cast(schema.rootInstance());
    }

    public void load() throws IOException {
        ConfigBinder.load(file, schema);
    }

    public void save() throws IOException {
        ConfigBinder.save(file, schema);
    }

    public void reload() throws IOException {
        load();
    }

    public static ConfigHandle of(Class<?> rootType, Path file) {
        return AnnotateConfig.builder(rootType, file).build();
    }

    public static ConfigHandle of(Object rootInstance, Path file) {
        return AnnotateConfig.builder(rootInstance, file).build();
    }
}
