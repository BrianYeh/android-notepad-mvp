import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
}

val allowClientOnlyBillingEntitlement = providers
    .gradleProperty("justNotes.allowClientOnlyBillingEntitlement")
    .map { value ->
        require(value == "true" || value == "false") {
            "justNotes.allowClientOnlyBillingEntitlement must be either true or false."
        }
        value
    }
    .orElse("false")

val enableBackendPurchaseFlow = providers
    .gradleProperty("justNotes.enableBackendPurchaseFlow")
    .map { value ->
        require(value == "true" || value == "false") {
            "justNotes.enableBackendPurchaseFlow must be either true or false."
        }
        value
    }
    .orElse("false")

fun String.normalizedBackendBaseUrl(): String {
    val normalized = trim().trimEnd('/')
    if (normalized.isEmpty()) return normalized
    val uri = runCatching { URI(normalized) }.getOrNull()
    require(
        uri?.scheme == "https" &&
            !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null &&
            uri.rawPath.isNullOrEmpty() &&
            uri.rawQuery == null &&
            uri.rawFragment == null
    ) {
        "justNotes.backendBaseUrl must be blank or an HTTPS origin without credentials, query, or fragment."
    }
    return normalized
}

fun String.normalizedGoogleWebClientId(): String {
    val normalized = trim()
    if (normalized.isEmpty()) return normalized
    require(Regex("^[A-Za-z0-9-]+\\.apps\\.googleusercontent\\.com$").matches(normalized)) {
        "justNotes.googleWebClientId must be a Google Web OAuth client ID."
    }
    return normalized
}

fun Provider<String>.quotedBuildConfigString(): String {
    val escaped = get()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return "\"$escaped\""
}

val backendBaseUrl = providers
    .gradleProperty("justNotes.backendBaseUrl")
    .map { it.normalizedBackendBaseUrl() }
    .orElse("")

val googleWebClientId = providers
    .gradleProperty("justNotes.googleWebClientId")
    .map { it.normalizedGoogleWebClientId() }
    .orElse("")

val uploadStoreFile = providers.gradleProperty("justNotes.uploadStoreFile")
val uploadStorePassword = providers.gradleProperty("justNotes.uploadStorePassword")
val uploadKeyAlias = providers.gradleProperty("justNotes.uploadKeyAlias")
val uploadKeyPassword = providers.gradleProperty("justNotes.uploadKeyPassword")
val uploadSigningConfigured = listOf(
    uploadStoreFile,
    uploadStorePassword,
    uploadKeyAlias,
    uploadKeyPassword,
).all { it.isPresent }
val injectedUploadStoreFile = providers.gradleProperty("android.injected.signing.store.file")
val injectedUploadStorePassword = providers.gradleProperty("android.injected.signing.store.password")
val injectedUploadKeyAlias = providers.gradleProperty("android.injected.signing.key.alias")
val injectedUploadKeyPassword = providers.gradleProperty("android.injected.signing.key.password")
val androidStudioSigningConfigured = listOf(
    injectedUploadStoreFile,
    injectedUploadStorePassword,
    injectedUploadKeyAlias,
    injectedUploadKeyPassword,
).all { it.isPresent }
val releaseSigningConfigured = uploadSigningConfigured || androidStudioSigningConfigured

android {
    namespace = "com.example.notepad"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.brianyeh.justnotes"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.0.8"

        testInstrumentationRunner = "com.example.notepad.JustNotesTestRunner"

        buildConfigField("String", "BACKEND_BASE_URL", backendBaseUrl.quotedBuildConfigString())
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", googleWebClientId.quotedBuildConfigString())
    }

    signingConfigs {
        if (uploadSigningConfigured) {
            create("releaseUpload") {
                storeFile = file(uploadStoreFile.get())
                storePassword = uploadStorePassword.get()
                keyAlias = uploadKeyAlias.get()
                keyPassword = uploadKeyPassword.get()
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "ENABLE_BACKEND_PURCHASE_FLOW", enableBackendPurchaseFlow.get())
            buildConfigField(
                "boolean",
                "ALLOW_CLIENT_ONLY_BILLING_ENTITLEMENT",
                allowClientOnlyBillingEntitlement.get(),
            )
        }
        release {
            if (uploadSigningConfigured) {
                signingConfig = signingConfigs.getByName("releaseUpload")
            }
            buildConfigField("boolean", "ENABLE_BACKEND_PURCHASE_FLOW", enableBackendPurchaseFlow.get())
            buildConfigField("boolean", "ALLOW_CLIENT_ONLY_BILLING_ENTITLEMENT", "false")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
            )
        }
    }
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    doFirst {
        require(releaseSigningConfigured) {
            "Release upload signing properties or Android Studio injected signing credentials are required for bundleRelease."
        }
    }
}

dependencies {
    val roomVersion = "2.6.1"

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("com.android.billingclient:billing:9.1.0")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.api-client:google-api-client-android:2.7.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20240628-2.0.0")
    implementation("com.google.http-client:google-http-client-gson:1.45.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")

    kapt("androidx.room:room-compiler:$roomVersion")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    androidTestImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
