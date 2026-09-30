import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.vanniktech.maven.publish")
}

group = "lk.payments"
version = "0.1.1"

android {
    namespace = "lk.payments.sdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.browser:browser:1.10.0")

    testImplementation("junit:junit:4.13.2")
}

// Maven Central, through the Central Portal. The public repository's release
// workflow runs publishAndReleaseToMavenCentral on a version tag, with the
// Portal token and the signing key from its secrets. Without a key (the
// monorepo's CI, a developer's machine) nothing is signed and nothing is
// published; publishToMavenLocal still works for trying a build in an app.
mavenPublishing {
    configure(
        AndroidSingleVariantLibrary(
            javadocJar = JavadocJar.Empty(),
            sourcesJar = SourcesJar.Sources(),
            variant = "release",
        ),
    )
    coordinates("lk.payments", "payments-lk-android", version.toString())
    publishToMavenCentral(automaticRelease = true)
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    pom {
        name.set("Payments.lk for Android")
        description.set("Open a Payments.lk hosted checkout from an Android app in a Custom Tab and read the outcome from the return.")
        inceptionYear.set("2026")
        url.set("https://payments.lk/developers/sdks#android")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/license/mit")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("payments-lk")
                name.set("Payments.lk")
                email.set("hello@payments.lk")
                url.set("https://payments.lk")
            }
        }
        scm {
            url.set("https://github.com/PAYable-IPG/payments-lk-android")
            connection.set("scm:git:https://github.com/PAYable-IPG/payments-lk-android.git")
            developerConnection.set("scm:git:ssh://git@github.com/PAYable-IPG/payments-lk-android.git")
        }
        issueManagement {
            system.set("GitHub")
            url.set("https://github.com/PAYable-IPG/payments-lk-android/issues")
        }
    }
}
