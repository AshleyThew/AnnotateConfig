plugins {
    `java-library`
    `maven-publish`
    jacoco
}

group = "com.github.AshleyThew"
version = System.getenv("TAG") ?: "v1.0.3-alpha"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.yaml:snakeyaml:2.2")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
        csv.required = false
    }
}

// The library compiles for Java 8 so plugins that still run on old server JVMs can shade it;
// --release also stamps the published metadata with JVM 8 so those consumers can resolve it.
// Tests keep Java 17 syntax.
tasks.compileJava {
    options.release = 8
}

tasks.compileTestJava {
    options.release = 17
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
