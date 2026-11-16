plugins {
    id("com.android.application")
    id("checkstyle")
}

apply {
    plugin("kotlin-android")
    plugin("kotlin-android-extensions")
}

repositories {
    jcenter()
    maven(url = "https://jitpack.io")
    google()
}

android {
    compileSdkVersion(28)

    defaultConfig {
        minSdkVersion(14)
        targetSdkVersion(28)  //'Q'.toInt()
        versionCode = 164
        versionName = "0.7.11"
    }

    compileOptions {
        targetCompatibility = JavaVersion.VERSION_1_8
        sourceCompatibility = JavaVersion.VERSION_1_8
    }

    splits {
        abi {
            setEnable(true)
            reset()
            include("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
            setUniversalApk(true)

        }
    }
}
dependencies {
    val preference_version = "1.1.0"
    val core_version = "1.1.0"
    val material_version = "1.0.0"

    implementation("androidx.annotation:annotation:1.1.0")
    implementation("androidx.core:core:1.1.0")

    // Is there a nicer way to do this?
    implementation("androidx.constraintlayout:constraintlayout:1.1.3")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.50")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.0.0")
    implementation("androidx.appcompat:appcompat:1.1.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.squareup.okhttp3:okhttp:3.2.0")
    implementation("androidx.core:core:$core_version")
    implementation("androidx.core:core-ktx:$core_version")

    implementation("org.jetbrains.anko:anko-commons:0.10.4")

    implementation("androidx.fragment:fragment-ktx:1.1.0")


    implementation("androidx.preference:preference:$preference_version")
    implementation("androidx.preference:preference-ktx:$preference_version")

    implementation("com.google.android.material:material:$material_version")


    testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.50")

    testImplementation("junit:junit:4.12")
    testImplementation("org.mockito:mockito-core:3.1.0")
    testImplementation("org.robolectric:robolectric:4.3.1")
    implementation(project(":main"))
}

