# AnnotateConfig

[![Build](https://img.shields.io/github/actions/workflow/status/AshleyThew/AnnotateConfig/build.yml?branch=main&label=build)](https://github.com/AshleyThew/AnnotateConfig/actions/workflows/build.yml)
[![Tests](https://img.shields.io/github/actions/workflow/status/AshleyThew/AnnotateConfig/ci.yml?branch=main&label=tests)](https://github.com/AshleyThew/AnnotateConfig/actions/workflows/ci.yml)
[![Coverage](https://codecov.io/gh/AshleyThew/AnnotateConfig/branch/main/graph/badge.svg)](https://codecov.io/gh/AshleyThew/AnnotateConfig)
[![JitPack](https://img.shields.io/jitpack/version/com.github.AshleyThew/AnnotateConfig)](https://jitpack.io/#AshleyThew/AnnotateConfig)

AnnotateConfig is a small standalone Java library for schema-driven YAML configs backed by static fields or config class instances with runtime annotations. It is designed for plugin and library projects that want commented YAML without depending on Bukkit, Paper, Velocity, Fabric, or any other server API.

The library scans a root config class, loads values into static or instance fields, rewrites the YAML with generated comments, and can preserve unknown keys so hand-added values are not discarded. The intended use is to publish it through JitPack and shade plus relocate it into consumer jars.

## Features

- No server implementation dependency
- Static and instance field binding
- Commented YAML generation
- Migration from legacy paths
- Custom serializers for plugin-specific types
- Enum, list, set, map, primitive, and string support
- JitPack-friendly Gradle build
- Safe to shade and relocate

## Example

```java
import me.dablakbandit.annotateconfig.AnnotateConfig;
import me.dablakbandit.annotateconfig.ConfigHandle;
import me.dablakbandit.annotateconfig.ConfigSerializer;
import me.dablakbandit.annotateconfig.NamingStrategy;
import me.dablakbandit.annotateconfig.annotation.ConfigComment;
import me.dablakbandit.annotateconfig.annotation.ConfigMigrate;
import me.dablakbandit.annotateconfig.annotation.ConfigOptional;
import me.dablakbandit.annotateconfig.annotation.ConfigRoot;

import java.nio.file.Path;
import java.util.List;

@ConfigRoot(
    header = {"Example config", "Generated on load/save"},
    naming = NamingStrategy.LOWER_KEBAB_CASE,
    preserveUnknownFields = true
)
public final class ExampleConfig {
    @ConfigComment("Enable the feature")
    public static boolean enabled = true;

    @ConfigComment("Maximum number of attempts")
    public static int maxRetries = 5;

    @ConfigComment("Legacy value migrated from old configs")
    @ConfigMigrate("legacy.enabled")
    public static boolean migratedEnabled = true;

    @ConfigComment("Text channels to allow")
    public static List<String> channels = List.of("global", "staff");

    @ConfigOptional
    @ConfigComment("Optional welcome message override")
    public static String welcomeMessage;

    @ConfigComment("Persistence settings")
    public static final class Storage {
        @ConfigComment("Storage type")
        public static String type = "sqlite";
    }
}

Path file = Path.of("config/example.yml");
ConfigHandle handle = AnnotateConfig.builder(ExampleConfig.class, file).build();
handle.load();
handle.save();
```

## Non-Static Nested Usage

You can also model sections as non-static nested classes.

```java
import me.dablakbandit.annotateconfig.AnnotateConfig;
import me.dablakbandit.annotateconfig.ConfigHandle;
import me.dablakbandit.annotateconfig.annotation.ConfigRoot;

import java.nio.file.Path;

@ConfigRoot
public class InstanceConfig {
    public int maxRetries = 3;
    public Database database;

    // Non-static nested section
    public class Database {
        public String host = "localhost";
        public int port = 3306;
    }
}

Path file = Path.of("config/instance.yml");

// Option 1: let AnnotateConfig create the root instance
ConfigHandle autoHandle = ConfigHandle.of(InstanceConfig.class, file);
autoHandle.load();
InstanceConfig autoRoot = autoHandle.rootInstance(InstanceConfig.class);
String host = autoRoot.database.host;

// Option 2: provide your own root instance
InstanceConfig providedRoot = new InstanceConfig();
ConfigHandle providedHandle = ConfigHandle.of(providedRoot, file);
providedHandle.load();
int port = providedRoot.database.port;
providedHandle.save();
```

For non-static nested sections, declare a root field of that nested type (for example `database`) so values are Java-accessible (`instance.database.host`) after `load()`.

## Custom Serializers

Register serializers on the builder for types that are not built into the core library.

```java
record SpawnPoint(String world, int x, int y, int z) {}

ConfigSerializer<SpawnPoint> serializer = new ConfigSerializer<>() {
    @Override
    public Object serialize(SpawnPoint value) {
        return Map.of(
            "world", value.world(),
            "x", value.x(),
            "y", value.y(),
            "z", value.z()
        );
    }

    @Override
    public SpawnPoint deserialize(Object raw) {
        Map<?, ?> values = (Map<?, ?>) raw;
        return new SpawnPoint(
            String.valueOf(values.get("world")),
            ((Number) values.get("x")).intValue(),
            ((Number) values.get("y")).intValue(),
            ((Number) values.get("z")).intValue()
        );
    }
};

ConfigHandle handle = AnnotateConfig.builder(ExampleConfig.class, file)
    .serializer(SpawnPoint.class, serializer)
    .build();
```

Serializers are also used recursively for `List<T>`, `Set<T>`, and `Map<K, V>` entries when `T`, `K`, or `V` has a registered serializer.

## Gradle

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.AshleyThew:AnnotateConfig:<tag>")
}
```

## Build And Test

```bash
./gradlew test jacocoTestReport
```

- Test report: `build/reports/tests/test/index.html`
- Coverage report: `build/reports/jacoco/test/html/index.html`

CI runs from `.github/workflows/ci.yml` (test and coverage reports) and `.github/workflows/build.yml` (release/tag build flow).

## Releases And JitPack

- Release workflow: `.github/workflows/build.yml`
- Create and push a tag like `v1.0.0` to trigger:
  - GitHub Release creation/update
  - JAR asset upload from `build/libs/*.jar`
  - JitPack build availability at `https://jitpack.io/#AshleyThew/AnnotateConfig/<tag>`
- JitPack build config: `jitpack.yml`

## Shade And Relocate

Shadow example:

```kotlin
plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation("com.github.AshleyThew:AnnotateConfig:<tag>")
}

tasks.shadowJar {
    relocate("me.dablakbandit.annotateconfig", "your.plugin.libs.annotateconfig")
    relocate("org.yaml.snakeyaml", "your.plugin.libs.snakeyaml")
}
```

## Notes

- Config fields can be static or instance fields, but must not be `final`.
- `@ConfigOptional` fields are omitted from the generated YAML when their value is `null`, but they still load when the key is present.
- Nested config sections can be static or instance-backed, but non-static sections require an enclosing config instance.
- `load()` rewrites the file so comments, migrated keys, and defaults stay in sync.
- Unknown keys are kept when `preserveUnknownFields = true`.
