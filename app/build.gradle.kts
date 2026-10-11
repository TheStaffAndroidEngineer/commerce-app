plugins { alias(libs.plugins.androidApplication); alias(libs.plugins.kotlinCompose); alias(libs.plugins.hilt); alias(libs.plugins.ksp) }
android {
    namespace = "staff.android.commerce"
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    buildToolsVersion = "37.0.0"
    defaultConfig {
        minSdk = 26
        applicationId = "staff.android.commerce"
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_26; targetCompatibility = JavaVersion.VERSION_26 }
}
kotlin { jvmToolchain(27); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26) } }
// Cloud configuration is explicitly opt-in; the default checkpoint is local and runnable.
val cloudReports = providers.gradleProperty("cloudReports").orNull == "true"
if (cloudReports) { apply(plugin = "com.google.gms.google-services"); apply(plugin = "com.google.firebase.crashlytics") }

dependencies {
    implementation(libs.pagingCompose)
    implementation(libs.timber)
    debugImplementation(libs.leakcanary)
    debugImplementation(libs.chucker)
    if (cloudReports) { implementation(platform(libs.firebaseBom)); implementation(libs.firebaseCrashlytics) }
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:catalog"))
    implementation(project(":feature:saved"))
    implementation(project(":feature:cart"))
    implementation(libs.androidxActivityCompose)
    implementation(libs.androidxLifecycleRuntimeCompose)
    implementation(libs.coroutinesAndroid)
    implementation(platform(libs.composeBom))
    implementation(libs.composeUi)
    implementation(libs.composeFoundation)
    implementation(libs.composeMaterial3)
    implementation(libs.androidxLifecycleViewModel)
    debugImplementation(libs.composeTestManifest)
    androidTestImplementation(platform(libs.composeBom))
    androidTestImplementation(libs.composeTestJunit4)
    androidTestImplementation(libs.androidxTestJunit)
    androidTestImplementation(libs.androidxTestRunner)
    androidTestImplementation(libs.espressoCore)
    androidTestImplementation(libs.androidxTestRules)
    implementation(libs.hiltAndroid)
    ksp(libs.hiltCompiler)
}

android.sourceSets.getByName("main").kotlin.directories.add(if (cloudReports) "src/cloud/kotlin" else "src/local/kotlin")
