plugins { alias(libs.plugins.android.application) }

android {
    namespace = "com.face.emotion.lanprobe"
    compileSdk = 36
    // Recovery build uses the SDK's prebuilt native libraries without stripping.
    packaging { jniLibs { keepDebugSymbols.add("**/*.so") } }
    defaultConfig {
        applicationId = "com.face.emotion.lanprobe.recovered"
        minSdk = 24
        targetSdk = 34
        versionCode = 9
        versionName = "3.0"
        testInstrumentationRunner = "com.face.emotion.lanprobe.CaptureInstrumentation"
        ndk { abiFilters.add("arm64-v8a") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("io.github.ezviz-open:ezviz-sdk:5.32")
    // Required by SDK at runtime; its Maven metadata does not pull these in.
    implementation("com.squareup.okhttp3:okhttp:3.12.1")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("junit:junit:4.13.2")
}
