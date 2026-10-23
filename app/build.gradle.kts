plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}   

android {
    namespace = "com.example.razoproject"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.razoproject"
        minSdk = 24
        targetSdk = 34
        versionCode = 8
        versionName = "1.2.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val defaultCityCareApiUrl = "http://10.0.2.2:5001/api"
        val cityCareApiUrl = providers.gradleProperty("CITYCARE_API_URL")
            .orElse(defaultCityCareApiUrl)
            .get()
        buildConfigField("String", "CITYCARE_API_URL", "\"$cityCareApiUrl\"")

        val facebookAppId = providers.gradleProperty("FACEBOOK_APP_ID").orElse("").get()
        resValue("string", "facebook_app_id", facebookAppId)
        resValue(
            "string",
            "facebook_client_token",
            providers.gradleProperty("FACEBOOK_CLIENT_TOKEN").orElse("").get()
        )
        resValue("string", "fb_login_protocol_scheme", "fb$facebookAppId")
        manifestPlaceholders["MAPS_API_KEY"] =
            providers.gradleProperty("MAPS_API_KEY").orElse("").get()
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
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
}

dependencies {
    // Firebase BoM & Google Services (Compatible with Kotlin 1.9)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.facebook.android:facebook-login:17.0.0")

    // Google Maps SDK, Maps Compose & Fused Location Provider
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("com.google.maps.android:maps-compose:4.3.3")

    implementation(libs.androidx.core.ktx)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}