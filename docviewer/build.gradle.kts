import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
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

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlin.test)
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
