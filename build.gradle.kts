import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `java-library`
}

group = "in.codelif"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    api(libs.serialization.json)
    api(libs.coroutines.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform { excludeTags("live", "dump") }
}

// read-only calls against the real portal, needs KTJIIT_LIVE_SESSION=<session.json>
tasks.register<Test>("liveTest") {
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("live") }
    environment("KTJIIT_LIVE_SESSION", System.getenv("KTJIIT_LIVE_SESSION") ?: "")
    outputs.upToDateWhen { false }
}

// dev only: parse every pdf in KTJIIT_PDF_DIR and write a summary to KTJIIT_DUMP_OUT
tasks.register<Test>("dumpMarks") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("dump") }
    environment("KTJIIT_PDF_DIR", System.getenv("KTJIIT_PDF_DIR") ?: "")
    environment("KTJIIT_DUMP_OUT", System.getenv("KTJIIT_DUMP_OUT") ?: "")
    environment("KTJIIT_JSON_OUT", System.getenv("KTJIIT_JSON_OUT") ?: "")
    outputs.upToDateWhen { false }
}
