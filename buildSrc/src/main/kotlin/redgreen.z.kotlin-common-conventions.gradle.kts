import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm")
    id("com.adarshr.test-logger")
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    }

    implementation("io.github.microutils:kotlin-logging-jvm:2.0.10")

    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.5.21"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    val kotestVersion = "4.6.1"
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")

    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")

    runtimeOnly("ch.qos.logback:logback-classic:1.2.3")
    runtimeOnly("ch.qos.logback:logback-core:1.2.3")
}

tasks.test {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}

val isIdea = System.getProperty("idea.version") != null

testlogger {
    // idea can't handle ANSI output
    setTheme(if (isIdea) "plain" else "mocha")
    showFullStackTraces = false
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        apiVersion = "1.5"
        languageVersion = "1.5"
        freeCompilerArgs = listOf("-Xjsr305=strict", "-progressive", "-Xopt-in=kotin.RequiresOptIn")
    }
}