plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Versionsnummer kommt automatisch aus GitHub Actions (Laufnummer des
// Bau-Workflows). Nicht mehr von Hand aendern - sonst laufen App-Version,
// Release-Tag und Update-Pruefung wieder auseinander.
// Lokal (ohne GitHub) gebaut: versionCode 1 / "1.0.0-lokal".
val buildNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0

android {
    namespace = "io.github.delcore1.heizung"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.delcore1.heizung"
        minSdk = 26
        targetSdk = 34
        versionCode = if (buildNumber > 0) buildNumber else 1
        versionName = if (buildNumber > 0) "1.0.$buildNumber" else "1.0.0-lokal"
    }

    // Release-Schluessel: liegt NICHT im Repository. GitHub Actions legt ihn
    // aus dem Secret SIGNING_KEYSTORE_B64 an und uebergibt die Passwoerter per
    // Umgebungsvariable. Ohne diese Angaben (lokaler Bau, Fork) wird nicht signiert.
    val ks = System.getenv("SIGNING_KEYSTORE")
    signingConfigs {
        if (ks != null) create("release") {
            storeFile = file(ks)
            storePassword = System.getenv("SIGNING_PASSWORD")
            keyAlias = "heizung"
            keyPassword = System.getenv("SIGNING_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (ks != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("com.google.android.material:material:1.12.0")

    // Netzwerk
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // Szenario-Tests (laufen auf GitHub vor jedem Release). org.json ist in
    // Android-Unit-Tests nur ein leerer Platzhalter, daher die echte Bibliothek.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
