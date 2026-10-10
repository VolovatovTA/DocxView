// Architecture rules as unit tests: `./gradlew :konsist:test`, part of `check`.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(libs.konsist)
    testImplementation(libs.junit)
}

tasks.test {
    // Konsist reads the sources of the whole repository, so changes anywhere must rerun it.
    inputs.dir(rootProject.layout.projectDirectory.dir("docviewer-core/src"))
    inputs.dir(rootProject.layout.projectDirectory.dir("docviewer/src"))
}
