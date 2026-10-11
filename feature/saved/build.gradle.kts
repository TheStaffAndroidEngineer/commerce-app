plugins { alias(libs.plugins.androidLibrary); alias(libs.plugins.kotlinCompose) }
android {
    namespace = "staff.android.commerce.saved"
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    buildToolsVersion = "37.0.0"
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_26; targetCompatibility = JavaVersion.VERSION_26 }
}
kotlin { jvmToolchain(27); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26) } }
dependencies {
    api(project(":core:model"))
    api(platform(libs.composeBom))
    api(libs.composeRuntime)
    implementation(libs.composeUi)
    implementation(libs.composeFoundation)
    implementation(libs.composeMaterial3)
    implementation(libs.coroutinesAndroid)
    implementation(project(":core:designsystem"))
}
