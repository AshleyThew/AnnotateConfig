package me.dablakbandit.annotateconfig.internal;

import java.lang.reflect.Field;
import java.util.List;

public record BoundField(
    Field field,
    String path,
    List<String> migrationPaths
) {
}
