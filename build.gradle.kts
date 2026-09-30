// Plugin versions for the whole build, put on the build script classpath. The
// library module applies the plugins by id without versions. The Maven publish
// plugin publishes to Maven Central from the SDK's public repository
// (github.com/PAYable-IPG/payments-lk-android); see the module's build file.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.13.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.20")
        classpath("com.vanniktech:gradle-maven-publish-plugin:0.37.0")
    }
}
