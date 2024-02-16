plugins {
    kotlin("jvm") version "1.8.21" apply false
    id("org.jetbrains.dokka") version "1.9.10" apply false
}

group = "com.catalystmonitor.client"
version = "0.0.1-SNAPSHOT"

subprojects {
    apply(plugin = "org.jetbrains.dokka")
}