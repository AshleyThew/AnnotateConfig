package me.dablakbandit.annotateconfig;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TestYaml {
    private static final Yaml YAML = new Yaml(new SafeConstructor(new LoaderOptions()));

    private TestYaml() {
    }

    static Map<String, Object> readMap(Path path) throws IOException {
        Object loaded = YAML.load(Files.readString(path));
        if (loaded == null) {
            return new LinkedHashMap<>();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) loaded;
        return map;
    }

    static Object get(Map<String, Object> root, String path) {
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    static boolean hasPath(Map<String, Object> root, String path) {
        String[] parts = path.split("\\.");
        Object current = root;
        for (int i = 0; i < parts.length; i++) {
            if (!(current instanceof Map<?, ?> map)) {
                return false;
            }
            if (!map.containsKey(parts[i])) {
                return false;
            }
            current = map.get(parts[i]);
            if (i < parts.length - 1 && current == null) {
                return false;
            }
        }
        return true;
    }

    static List<String> readLines(Path path) throws IOException {
        return Files.readAllLines(path);
    }
}
