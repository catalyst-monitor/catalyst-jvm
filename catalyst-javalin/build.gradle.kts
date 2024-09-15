plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
    signing
}

group = "com.catalystmonitor.client"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation(project(":catalyst-core"))
    implementation("io.javalin:javalin:6.0.0")
    testImplementation("io.javalin:javalin-testtools:6.0.0")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation(platform("io.opentelemetry:opentelemetry-bom:1.41.0"))
    testImplementation("io.opentelemetry:opentelemetry-sdk")
    testImplementation("io.opentelemetry:opentelemetry-api")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
    testImplementation("io.opentelemetry.semconv:opentelemetry-semconv:1.26.0-alpha")
    testImplementation("org.slf4j:slf4j-simple:2.0.11")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

java {
    withSourcesJar()
}

val dokkaHtml by tasks.getting(org.jetbrains.dokka.gradle.DokkaTask::class)

val javadocJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    dependsOn(dokkaHtml)
    archiveClassifier.set("javadoc")
    from(dokkaHtml.outputDirectory)
}

publishing {
    publications {
        create<MavenPublication>("catalyst-javalin") {
            artifact(javadocJar)
            from(components["java"])

            pom {
                name.set("Catalyst Javalin Client")
                description.set("Monitors Javalin with Catalyst.")
                url.set("https://www.catalystmonitor.com")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        name.set("Bill Liu")
                        email.set("bill@privium.xyz")
                        organization.set("Privium Inc.")
                        organizationUrl.set("https://www.catalystmonitor.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/catalyst-monitor/catalyst-jvm")
                    developerConnection.set("scm:git:ssh://github.com/catalyst-monitor/catalyst-jvm")
                    url.set("https://github.com/catalyst-monitor/catalyst-jvm")
                }
            }
        }
    }

    repositories {
        maven {
            name = "myRepo"
            url = uri("file:///home/bill/Work/myRepo")
        }
    }
}

signing {
    sign(publishing.publications["catalyst-javalin"])
}