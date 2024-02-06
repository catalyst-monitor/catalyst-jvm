pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

}
rootProject.name = "catalyst-monitor"

include("catalyst-core")
include("catalyst-log4j2")
include("catalyst-javalin")