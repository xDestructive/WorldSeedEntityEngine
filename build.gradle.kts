plugins {
    id("java")
    `maven-publish`
    signing
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25

    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://jitpack.io")
}

publishing {
    publications.create<MavenPublication>("maven") {
        groupId = "net.worldseed.multipart"
        artifactId = "WorldSeedEntityEngine"
        // 11.5.8: animation rotation sign fix — FrameProvider.RotationMul is now identity (1,1,1)
        // so animations match the raw rest-rotation convention. Plus Blockbench format-5.0
        // group-pivot resolution (GeoGenerator).
        version = "11.5.8"

        from(components["java"])
    }

    repositories {
        maven {
            name = "AtlasEngine"
            url = uri("https://reposilite.atlasengine.ca/public")
            credentials(PasswordCredentials::class)
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}

dependencies {
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    // Gradle 9 no longer adds the JUnit Platform launcher to the test runtime automatically.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    compileOnly(libs.minestom)
    testImplementation(libs.minestom)

    implementation(libs.commons.io)
    implementation(libs.zt.zip)

    implementation(libs.javax.json.api)
    implementation(libs.javax.json)

    implementation(libs.mql)
}

tasks.test {
    useJUnitPlatform()
}