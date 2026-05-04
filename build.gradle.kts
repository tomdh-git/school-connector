plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    id("maven-publish")
}

group = "com.tomdh.courseapi"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.tomdh-git:session-aware-web-client:master-SNAPSHOT")
    implementation("org.springframework.boot:spring-boot-starter-webflux:3.3.4")
    implementation("org.springframework.boot:spring-boot-starter-actuator:3.3.4")
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.8.1")
    implementation("org.springframework.boot:spring-boot-starter-cache:3.3.4")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
