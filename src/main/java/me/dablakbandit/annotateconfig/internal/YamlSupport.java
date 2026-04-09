package me.dablakbandit.annotateconfig.internal;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class YamlSupport {
    private static final Yaml LOADER = new Yaml(new SafeConstructor(new LoaderOptions()));
    private static final DumperOptions DUMPER_OPTIONS = defaultDumperOptions();
    private static final Yaml WRITER = new Yaml(new PlainMappingRepresenter(DUMPER_OPTIONS), DUMPER_OPTIONS);

    private YamlSupport() {
    }

    static Map<String, Object> loadMap(String content) {
        if (content == null || content.isBlank()) {
            return new LinkedHashMap<>();
        }
        Object loaded = LOADER.load(content);
        if (loaded == null) {
            return new LinkedHashMap<>();
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Root YAML node must be a map");
        }
        return toLinkedMap(map);
    }

    static String dumpWithComments(Map<String, Object> data, Map<String, List<String>> comments, List<String> header) {
        String dumped = WRITER.dump(data);
        if ("{}\n".equals(dumped)) {
            dumped = "";
        }

        StringBuilder builder = new StringBuilder();
        for (String line : header) {
            appendComment(builder, 0, line);
        }
        if (!header.isEmpty() && !dumped.isEmpty()) {
            builder.append('\n');
        }
        builder.append(injectComments(dumped, comments));
        return builder.toString();
    }

    static boolean containsPath(Map<String, Object> root, String path) {
        return getValue(root, path) != null || hasExplicitNull(root, path);
    }

    static Object getValue(Map<String, Object> root, String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
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

    static void setValue(Map<String, Object> root, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = root;
        for (int index = 0; index < parts.length - 1; index++) {
            Object next = current.get(parts[index]);
            if (!(next instanceof Map<?, ?>)) {
                LinkedHashMap<String, Object> created = new LinkedHashMap<>();
                current.put(parts[index], created);
                current = created;
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> nested = (Map<String, Object>) next;
            current = nested;
        }
        current.put(parts[parts.length - 1], value);
    }

    static Object removeValue(Map<String, Object> root, String path) {
        String[] parts = path.split("\\.");
        List<Map<String, Object>> lineage = new ArrayList<>();
        Map<String, Object> current = root;
        lineage.add(current);
        for (int index = 0; index < parts.length - 1; index++) {
            Object next = current.get(parts[index]);
            if (!(next instanceof Map<?, ?> nested)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> nestedMap = (Map<String, Object>) nested;
            current = nestedMap;
            lineage.add(current);
        }
        Object removed = current.remove(parts[parts.length - 1]);
        for (int index = lineage.size() - 1; index > 0; index--) {
            Map<String, Object> child = lineage.get(index);
            if (!child.isEmpty()) {
                break;
            }
            Map<String, Object> parent = lineage.get(index - 1);
            parent.remove(parts[index - 1]);
        }
        return removed;
    }

    private static String injectComments(String dumped, Map<String, List<String>> comments) {
        String[] lines = dumped.split("\\n", -1);
        int currentDepth = 0;
        List<String> pathParts = new ArrayList<>();
        StringBuilder builder = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                builder.append('\n');
                continue;
            }
            if (trimmed.startsWith("-")) {
                builder.append(line).append('\n');
                continue;
            }

            int depth = indentationDepth(line);
            String key = keyFromLine(trimmed);
            if (key != null) {
                while (pathParts.size() > depth) {
                    pathParts.remove(pathParts.size() - 1);
                }
                if (pathParts.size() == depth) {
                    pathParts.add(key);
                } else if (pathParts.size() > depth) {
                    pathParts.set(depth, key);
                }
                currentDepth = depth;
                String path = String.join(".", pathParts);
                List<String> commentLines = comments.get(path);
                if (commentLines != null) {
                    for (String comment : commentLines) {
                        appendComment(builder, currentDepth, comment);
                    }
                }
            }
            builder.append(line).append('\n');
        }
        return builder.toString();
    }

    private static int indentationDepth(String line) {
        int spaces = 0;
        while (spaces < line.length() && line.charAt(spaces) == ' ') {
            spaces++;
        }
        return spaces / 2;
    }

    private static String keyFromLine(String trimmed) {
        int separatorIndex = trimmed.indexOf(':');
        if (separatorIndex < 0) {
            return null;
        }
        return trimmed.substring(0, separatorIndex).trim();
    }

    private static void appendComment(StringBuilder builder, int depth, String comment) {
        builder.append("  ".repeat(Math.max(0, depth)));
        if (comment.startsWith("#")) {
            builder.append(comment);
        } else {
            builder.append("# ").append(comment);
        }
        builder.append('\n');
    }

    private static boolean hasExplicitNull(Map<String, Object> root, String path) {
        String[] parts = path.split("\\.");
        Object current = root;
        for (int index = 0; index < parts.length - 1; index++) {
            if (!(current instanceof Map<?, ?> map)) {
                return false;
            }
            current = map.get(parts[index]);
        }
        return current instanceof Map<?, ?> map && map.containsKey(parts[parts.length - 1]);
    }

    private static LinkedHashMap<String, Object> toLinkedMap(Map<?, ?> map) {
        LinkedHashMap<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            converted.put(String.valueOf(entry.getKey()), toYamlValue(entry.getValue()));
        }
        return converted;
    }

    private static Object toYamlValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return toLinkedMap(map);
        }
        if (value instanceof List<?> list) {
            List<Object> converted = new ArrayList<>(list.size());
            for (Object element : list) {
                converted.add(toYamlValue(element));
            }
            return converted;
        }
        return value;
    }

    private static DumperOptions defaultDumperOptions() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(false);
        options.setIndent(2);
        options.setIndicatorIndent(1);
        options.setSplitLines(false);
        return options;
    }

    private static final class PlainMappingRepresenter extends Representer {
        private PlainMappingRepresenter(DumperOptions options) {
            super(options);
        }

        @Override
        protected MappingNode representJavaBean(Set<Property> properties, Object javaBean) {
            MappingNode node = super.representJavaBean(new LinkedHashSet<>(properties), javaBean);
            node.setTag(Tag.MAP);
            return node;
        }
    }
}
