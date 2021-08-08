plugins {
    id("redgreen.z.kotlin-library-conventions")
    id("com.avast.gradle.docker-compose") version "0.14.5"
}

dependencies {
    implementation("org.zalando:fahrschein:0.18.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.3")

    implementation(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.5.1"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    implementation(platform("io.projectreactor:reactor-bom:2020.0.9"))
    implementation("io.projectreactor:reactor-core")
    testImplementation("io.projectreactor:reactor-test")

    testImplementation("io.rest-assured:rest-assured:4.4.0")
}

tasks {
    register("createEventType", Exec::class.java) {
        group = "Nakadi"
        description = "register event types"
        shouldRunAfter("composeUp")

        val payload = """
                {
                  "name": "sales-order-placed",
                  "owning_application": "order-service",
                  "category": "undefined",
                  "partition_strategy": "random",
                  "schema": {
                    "type": "json_schema",
                    "schema": "{ \"properties\": { \"order_number\": { \"type\": \"string\" } } }"
                  }
                }
            """

        commandLine(
            "curl", "-v", "-X", "POST",
            "http://localhost:8080/event-types",
            "-H", "Content-type: application/json",
            "-d", payload
        )
    }

    "test" {
        dependsOn("composeUp", "createEventType")
        finalizedBy("composeDown")
    }
}
