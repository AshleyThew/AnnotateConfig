package me.dablakbandit.annotateconfig.internal;

import java.lang.reflect.Field;
import java.util.List;

public final class BoundField {
    private final Field field;
    private final String path;
    private final List<String> migrationPaths;
    private final Object target;
    private final boolean optional;

    public BoundField(Field field, String path, List<String> migrationPaths, Object target, boolean optional) {
        this.field = field;
        this.path = path;
        this.migrationPaths = migrationPaths;
        this.target = target;
        this.optional = optional;
    }

    public Field field() {
        return field;
    }

    public String path() {
        return path;
    }

    public List<String> migrationPaths() {
        return migrationPaths;
    }

    public Object target() {
        return target;
    }

    public boolean optional() {
        return optional;
    }

    @Override
    public String toString() {
        return "BoundField[field=" + field + ", path=" + path + ", migrationPaths=" + migrationPaths
                + ", target=" + target + ", optional=" + optional + "]";
    }
}
