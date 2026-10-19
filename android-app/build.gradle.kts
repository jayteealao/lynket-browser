plugins {
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.androidLibrary) apply false
  alias(libs.plugins.kotlinParcelize) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.hilt) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.androidTest) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.room3) apply false
  alias(libs.plugins.androidx.baselineprofile) apply false
}

// The daemon JVM criteria (gradle/gradle-daemon-jvm.properties) name the JDK version only, with no
// download URLs: Gradle uses a matching local JDK and needs no toolchain download repository.
// Change the version with ./gradlew updateDaemonJvm --jvm-version=<n>.
tasks.named<org.gradle.buildconfiguration.tasks.UpdateDaemonJvm>("updateDaemonJvm") {
  toolchainPlatforms.empty()
}

tasks.register("clean", Delete::class) {
  delete(rootProject.layout.buildDirectory)
}
