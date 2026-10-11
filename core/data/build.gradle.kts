plugins { alias(libs.plugins.androidLibrary); alias(libs.plugins.kotlinSerialization); alias(libs.plugins.ksp); alias(libs.plugins.room3) }
android {
    namespace = "staff.android.commerce.data"
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    buildToolsVersion = "37.0.0"
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_26; targetCompatibility = JavaVersion.VERSION_26 }
    defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    testOptions { unitTests.isIncludeAndroidResources = true; unitTests.all { it.useJUnitPlatform(); it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED") } }
}
kotlin { jvmToolchain(27); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26) } }
dependencies {
    api(project(":core:model"))
    implementation(libs.coroutinesAndroid)
    implementation(libs.retrofit)
    implementation(libs.retrofitSerialization)
    implementation(libs.serializationJson)
    api(libs.okhttp)
    implementation(libs.room3Runtime)
    implementation(libs.room3Paging)
    implementation(libs.sqliteBundled)
    implementation(libs.pagingRuntime)
    ksp(libs.room3Compiler)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junitVintage)
    testImplementation(libs.mockWebServer)
    testImplementation(libs.turbine)
    testImplementation(libs.pagingTesting)
    androidTestImplementation(libs.androidxTestJunit)
    androidTestImplementation(libs.androidxTestRunner)
    testImplementation(libs.coroutinesTest)
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

room3 { schemaDirectory("$projectDir/schemas") }
