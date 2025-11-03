plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.guruyuknow.hisabbook"
    compileSdk = 35 // Change from 34 to 35 to match targetSdk

    defaultConfig {
        applicationId = "com.guruyuknow.hisabbook"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Navigation Components
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")

    // Google Sign In
    implementation("com.google.android.gms:play-services-auth:20.4.1")

    // Supabase
    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.0.4")
    implementation("io.github.jan-tennert.supabase:gotrue-kt:2.0.4")
    implementation("io.github.jan-tennert.supabase:storage-kt:2.0.4")
    implementation("io.ktor:ktor-client-android:2.3.7")
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("com.github.bumptech.glide:glide:4.14.2")
    annotationProcessor("com.github.bumptech.glide:compiler:4.14.2")
    implementation(platform("com.google.firebase:firebase-bom:32.8.1"))
    implementation("com.google.firebase:firebase-analytics")
    // Fragment
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation ("androidx.gridlayout:gridlayout:1.0.0")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation ("androidx.work:work-runtime-ktx:2.8.1")
    implementation ("com.google.android.material:material:1.12.0")
    implementation ("com.vanniktech:emoji-google:0.18.0")
    implementation ("io.coil-kt:coil:2.6.0")
    implementation ("com.google.android.gms:play-services-location:21.3.0")
    implementation ("com.github.PhilJay:MPAndroidChart:v3.1.0")
}