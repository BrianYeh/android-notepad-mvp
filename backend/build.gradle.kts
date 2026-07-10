plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

application {
    mainClass.set("com.brianyeh.justnotes.backend.BackendApplicationKt")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

dependencies {
    val ktorVersion = "2.3.12"

    implementation(platform("com.google.cloud:libraries-bom:26.85.0"))
    implementation("com.google.cloud:google-cloud-firestore")
    implementation("com.google.cloud:google-cloud-kms")
    implementation("com.google.cloud:google-cloud-secretmanager")
    implementation("com.google.auth:google-auth-library-oauth2-http")
    implementation("com.google.api-client:google-api-client")
    implementation("com.google.apis:google-api-services-androidpublisher:v3-rev20260706-2.0.0")
    implementation("com.google.http-client:google-http-client-gson")
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
}
