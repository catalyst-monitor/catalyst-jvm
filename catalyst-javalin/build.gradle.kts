plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
    signing
}

group = "com.catalystmonitor.client"
version = "0.0.2"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation(project(":catalyst-core"))
    implementation("io.javalin:javalin:6.0.0")
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