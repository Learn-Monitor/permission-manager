plugins {
    java
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = "io.github.learn-monitor"

version = "v1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "Student Database Snapshot Repository"
        url = uri("https://maven.pkg.github.com/Learn-Monitor/student-database/")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    // Logging
    compileOnly("org.slf4j:slf4j-api:2.0.13")

    // Main project
    compileOnly("io.github.learn-monitor:student-database:s2607b5-SNAPSHOT")
    compileOnly("io.github.learn-monitor:plugin-loader:v1.0.6")

    // For debugging
    runtimeOnly("io.github.learn-monitor:student-database:s2607b5-SNAPSHOT")

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4") // using JUnit 5 (latest)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("test.environment", "true")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17)) // or another version you prefer
    }
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "permission-manager", version.toString())

    pom {
        name = "Permission Manager"
        description = "Permission Manager for the Student Database project"
        licenses {
            license {
                name = "GNU General Public License v3.0"
                url = "http://www.gnu.org/licenses/gpl-3.0.txt"
            }
        }
        developers {
            developer {
                id = "schlaumeier5"
                name = "Lukas Morgenstern"
                url = "https://github.com/schlaumeier5"
            }
        }
    }
}

// For deploying the plugin jar to the debug-run/plugins directory for testing
tasks.withType<Jar> {
    manifest {
        attributes["Implementation-Title"] = "Example Plugin"
        attributes["Implementation-Version"] = project.version
    }
}

tasks.register<Copy>("deployPluginJar") {
    dependsOn(tasks.jar)

    from(tasks.jar)
    into(layout.buildDirectory.dir("debug-run/plugins"))
}
val runtimeClasspath = configurations.runtimeClasspath

tasks.register("printRuntimeClasspath") {
    val classpath = runtimeClasspath.map { it.asPath }

    doLast {
        println(classpath.get())
    }
}