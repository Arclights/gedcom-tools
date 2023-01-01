import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.20"
    application
}

group = "com.arclights"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("ch.qos.logback", "logback-classic", "1.4.5")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter", "junit-jupiter-params", "5.0.0")
    testImplementation("org.assertj:assertj-core:3.23.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "16"
}

application {
    mainClass.set("MainKt")
}