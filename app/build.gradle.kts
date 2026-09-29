import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// One number, read from gradle.properties, turned into every other form of itself.
val appVersion = (project.findProperty("appVersion") as String? ?: "1").trim().toInt()

// The signing key is a repository secret restored by the workflow. Locally the file is absent and
// the release build falls back to unsigned, which is correct: a build made on a desk is not a
// build that may be delivered (delivery-gate.md G1).
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// NO KEY REACHES THIS BUILD, IN ANY FORM. Baba, 14.9.2026, after a Maps key went out inside a
// public APK: "This is public app. My key cannot be inside. Only work with key picker. Key picker
// is the key." So there is no manifest placeholder, no BuildConfig field and no repository secret
// for a service key: every key arrives on the phone, from a file he picks (Keys.kt).

android {
    namespace = "com.mantra.arkod"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mantra.arkod"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersion
        versionName = appVersion.toString()
        // NO KEY IN THE BUILD (Mantra ARKOD, 29.9.2026): Google's map is drawn from the Map Tiles
        // API with the key each person makes and enters on the phone; the screen says how.
    }

    signingConfigs {
        if (keystoreProperties.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProperties.getProperty("storeFile") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // G3: Lint blocking from the first build. Narrow it in the session it cries wolf, never carry it.
    lint {
        ignoreTestSources = false

        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = true
        htmlReport = false
        xmlReport = true
        // Mapsforge is a Java library built before the AndroidX era; its own API surface is not
        // something this app can fix, and a blanket block on it would stop the build for somebody
        // else's code rather than for ours.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "ObsoleteLintCustomCheck")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        allWarningsAsErrors = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
        // Kept from when two theme jars were on the path: harmless with one, and the day a
        // second library ships a laundrette symbol again this will not fail the build.
        resources.pickFirsts += setOf("assets/symbols/**", "assets/patterns/**")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.documentfile:documentfile:1.0.1")
    // A modern fragment, so registerForActivityResult is safe whatever a library drags in
    // (InvalidFragmentVersionForActivityResult, Mantra Trail build 3).
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // THE MAP ENGINE, AND THERE IS ONE (16.9.2026). The CPU tile renderer was removed with the
    // lag it caused; VTM reads the same .map files on the GPU. The natives are its tile decoder.
    // instead of rasterised tile by tile on the CPU — which is the whole of why Google's map
    // turns and zooms smoothly and this one did not. LGPL-3.0, the same licence as mapsforge,
    // which this app already carries.
    // The GL tile decoder is native, one library per architecture. arm64 is his phone; the other
    // two are here so an APK handed to somebody else still runs.

    // VTM: MAPSFORGE'S OWN OPENGL RENDERER (16.9.2026). Same project, same version, and it reads
    // the very same .map files — what differs is where the drawing happens. mapsforge rasterises
    // tiles on the CPU and hands up bitmaps; VTM sends the geometry to the GPU, so a zoom or a
    // turn is a matrix per frame rather than sixty tiles re-rendered. Same LGPL as mapsforge,
    // which this app already carries. The natives are the tile decoder, one jar per architecture.
    implementation("org.mapsforge:vtm:0.25.0")
    implementation("org.mapsforge:vtm-android:0.25.0")
    implementation("org.mapsforge:vtm-themes:0.25.0")
    // VTM'S OWN HTTP CLIENT DOES NOT SPEAK HTTPS (17.9.2026). Its LwHttp says so in its own
    // comments — "no https, full header parsing or other stuff" — so every raster tile from
    // Google came back as nothing at all while the routing, which uses Android's own HTTP, worked
    // perfectly. This is the engine VTM ships for exactly that, and OkHttp is what it needs.
    // NO MAPS SDK (Mantra ARKOD, 29.9.2026). It reads its key from the manifest, which would put
    // one key in every APK; this app draws Google's tiles with VTM and the person's own key.
    implementation("org.mapsforge:vtm-http:0.25.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    runtimeOnly("org.mapsforge:vtm-android:0.25.0:natives-arm64-v8a")
    runtimeOnly("org.mapsforge:vtm-android:0.25.0:natives-armeabi-v7a")
    runtimeOnly("org.mapsforge:vtm-android:0.25.0:natives-x86_64")



    // androidsvg is NOT declared here: vtm-android already brings it, and declaring the aar as
    // well put every class in it on the path twice (checkReleaseDuplicateClasses, build 2).

    // The fix: GPS, Wi-Fi and cell fused by the system, plus the raw satellite status underneath it.
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // NO GOOGLE MAPS SDK. It reads its key from the installed app, which is exactly the thing
    // that put a live key inside a public APK. Google's tiles now come from the Map Tiles API
    // with the key from the picker, over plain HTTP, like any other tile service.

    testImplementation("junit:junit:4.13.2")
    // The real org.json for Test 1: android.jar carries only stubs that throw, and Parcels.kt
    // reads the cadastre's answers with it (27.9.2026).
    testImplementation("org.json:json:20240303")
}
