import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}
// 릴리스 서명 정보는 저장소 밖(기본 ~/PhilMission-signing/keystore.properties, 환경변수 PHILMISSION_KEYSTORE_PROPS로 변경 가능)에 둔다.
// 파일이 없으면 서명 없는 릴리스 APK가 만들어진다(다른 PC에서도 빌드는 가능).
val signingProps = Properties().apply {
    val path = System.getenv("PHILMISSION_KEYSTORE_PROPS") ?: "${System.getProperty("user.home")}/PhilMission-signing/keystore.properties"
    val file = File(path)
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "org.philmission.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.philmission.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        if (signingProps.containsKey("storeFile")) {
            create("release") {
                storeFile = File(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // 번역 라이브러리의 네이티브 파일이 CPU별로 들어 있어 용량이 크다. 배포용은 최신 폰(64비트 ARM)만 포함한다.
            // 에뮬레이터(x86)에서는 실행되지 않으므로 에뮬레이터 확인은 debug 빌드로 한다.
            ndk { abiFilters += "arm64-v8a" }
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    kapt("androidx.room:room-compiler:2.7.1")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("com.google.mlkit:translate:17.0.3")
    testImplementation("junit:junit:4.13.2")
}
tasks.register("verifyReleaseContent", Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("uv", "run", "python", "tools/validate_content.py", "--release")
}
tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn("verifyReleaseContent")
}
