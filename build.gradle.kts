plugins {
    kotlin("jvm") version "2.4.0"
    application
}

group = "com.arclights"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("ch.qos.logback", "logback-classic", "1.4.5")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.4.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter", "junit-jupiter-params", "5.0.0")
    testImplementation("org.assertj:assertj-core:3.23.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(26)
}

application {
    mainClass.set("MainKt")
}