import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
}

android {
    namespace = "io.github.volovatovta.docviewer"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        sarifReport = true
        htmlReport = true
        // Dependabot owns version bumps; lint should judge the code, not the calendar.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }

    // The instrumentation APK otherwise targets minSdk, which Android 14+ refuses to install.
    testOptions {
        targetSdk = 36
    }
}

kotlin {
    explicitApi()

    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api(project(":docviewer-core"))
    // Compose types (Modifier, Color, Dp, Saver, @Composable) are part of the public API.
    api(libs.compose.runtime)
    api(libs.compose.runtime.saveable)
    api(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    // Fails a test when an Activity, a composition or a document outlives it.
    androidTestImplementation(libs.leakcanary.instrumentation)
    androidTestImplementation(libs.androidx.activity.compose)
    androidTestRuntimeOnly(libs.androidx.test.runner)
    // Espresso before 3.7 injects input through an InputManager API removed in Android 16 QPR2.
    androidTestRuntimeOnly(libs.androidx.test.espresso.core)
    debugRuntimeOnly(libs.compose.ui.test.manifest)
}

mavenPublishing {
    configure(AndroidSingleVariantLibrary(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml")))
    publishToMavenCentral()
    // Maven Central requires signed artifacts; local publishing works without a key.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}

// AGP 9 compiles Kotlin itself, which the binary-compatibility-validator plugin cannot see yet,
// so its tasks are wired to the release classes by hand. Names match the plugin's: apiCheck, apiDump.
val apiBuild = tasks.register<KotlinApiBuildTask>("apiBuild") {
    inputClassesDirs.from(tasks.named<KotlinJvmCompile>("compileReleaseKotlin").flatMap { it.destinationDirectory })
    outputApiFile.set(layout.buildDirectory.file("api/${project.name}.api"))
}
val apiCheck = tasks.register<KotlinApiCompareTask>("apiCheck") {
    group = "verification"
    projectApiFile.set(layout.projectDirectory.file("api/${project.name}.api"))
    generatedApiFile.set(apiBuild.flatMap { it.outputApiFile })
}
tasks.register<Copy>("apiDump") {
    group = "other"
    from(apiBuild.flatMap { it.outputApiFile })
    into(layout.projectDirectory.dir("api"))
}
tasks.named("check") { dependsOn(apiCheck) }
