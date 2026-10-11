plugins { alias(libs.plugins.kotlinJvm); `java-library` }
kotlin {
    jvmToolchain(27)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26) }
}
java { sourceCompatibility = JavaVersion.VERSION_26; targetCompatibility = JavaVersion.VERSION_26 }
tasks.withType<JavaCompile>().configureEach { options.release.set(26) }
dependencies {
    api(libs.pagingCommon)
    api(libs.coroutinesCore) }
