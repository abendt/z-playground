/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    id("redgreen.z.kotlin-application-conventions")
}

dependencies {
    implementation(project(":utilities"))
}

application {
    // Define the main class for the application.
    mainClass.set("redgreen.z.app.AppKt")
}
