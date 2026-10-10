import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.github.volovatovta.docviewer"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    explicitApi()
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api(project(":docviewer-core"))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
}

mavenPublishing {
    // AGP's bundled Dokka can't read Kotlin 2.2 sealed classes, so ship an empty javadoc jar
    // (accepted by Maven Central), same as the JVM module.
    configure(AndroidSingleVariantLibrary(javadocJar = JavadocJar.Empty()))
    publishToMavenCentral()
    // Maven Central requires signed artifacts; local publishing works without a key.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}
