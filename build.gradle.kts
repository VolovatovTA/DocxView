import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

buildscript {
    // dependency-analysis ships a newer kotlin-bom that would upgrade the Kotlin compiler client under KGP.
    configurations.classpath { exclude(group = "org.jetbrains.kotlin", module = "kotlin-bom") }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.dependency.analysis)
    alias(libs.plugins.module.graph.assert)
}

// Raise when coverage grows; a pull request that lowers it must explain why.
val minimumLineCoverage = 50

// Public API dumps live in <module>/api; `apiCheck` (part of `check`) fails on unintended changes.
apiValidation {
    ignoredProjects += listOf("sample", "konsist")
    nonPublicMarkers += "kotlin.PublishedApi"
}

// Merged unit-test coverage: `./gradlew koverHtmlReport` for humans, `koverXmlReport` for CI.
dependencies {
    kover(project(":docviewer-core"))
    kover(project(":docviewer"))
}

kover {
    reports {
        // Measures logic, not pixels: composables and generated classes are excluded.
        filters {
            excludes {
                annotatedBy("androidx.compose.runtime.Composable")
                classes("*ComposableSingletons*", "*.BuildConfig", "*.R", "*.R$*")
            }
        }
        verify {
            rule("line coverage must not fall below the baseline") {
                minBound(minimumLineCoverage)
            }
        }
    }
}

// Module dependency rules, checked by `./gradlew assertModuleGraph`.
moduleGraphAssert {
    maxHeight = 3
    allowed = arrayOf(
        ":sample -> :docviewer",
        ":docviewer -> :docviewer-core",
    )
    restricted = arrayOf(
        // The parsers stay a pure JVM library that anyone can use without Android.
        ":docviewer-core -X> :.*",
    )
}

allprojects {
    group = "io.github.volovatovta.docviewer"
    version = "1.0.0"
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    if (name in setOf("docviewer-core", "docviewer")) apply(plugin = "org.jetbrains.kotlinx.kover")

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        source.setFrom("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin")
    }

    dependencies {
        "detektPlugins"(rootProject.libs.detekt.formatting)
        "detektPlugins"(rootProject.libs.detekt.compose.rules)
    }

    // Detekt 1.23 runs on its own Kotlin compiler; keep the build's newer Kotlin off its classpath.
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion("2.0.21")
        }
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            html.required.set(true)
            sarif.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
            md.required.set(false)
        }
    }
}
