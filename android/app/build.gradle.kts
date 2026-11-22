import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val backendUrl = providers.gradleProperty("romearBackendUrl").orElse("").get().trim().trimEnd('/')
val emailAuth = providers.gradleProperty("romearEmailAuth").orElse("false").get().toBooleanStrict()

android {
    namespace = "app.romear"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.romear"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (backendUrl.isNotEmpty()) {
            val uri = URI(backendUrl)
            require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null &&
                (uri.path.isEmpty() || uri.path == "/") &&
                (uri.scheme == "https" || (uri.scheme == "http" && uri.host == "127.0.0.1"))) {
                "romearBackendUrl must be an HTTPS origin or a debug loopback origin"
            }
        }
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
        require(!emailAuth || backendUrl.startsWith("https://") || backendUrl.startsWith("http://127.0.0.1:")) {
            "romearEmailAuth requires a backend URL"
        }
        buildConfigField("boolean", "EMAIL_AUTH", emailAuth.toString())
    }
    buildFeatures { viewBinding = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        debug { applicationIdSuffix = if (backendUrl.startsWith("http://")) ".mock.local" else ".mock" }
        release { isMinifyEnabled = false }
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
