import com.google.protobuf.gradle.id

plugins {
    kotlin("jvm")
    id("com.google.protobuf") version "0.9.4"
    `java-library`
    `maven-publish`
    signing
}

group = "com.catalystmonitor.client"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.9.3")
    // Fix error for mockk: https://github.com/mockk/mockk/issues/397
    testRuntimeOnly("net.bytebuddy:byte-buddy:1.10.21")

    implementation("com.google.protobuf:protobuf-java:3.22.2")
    implementation("com.google.protobuf:protobuf-kotlin:3.22.2")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

tasks.register<Jar>("sourcesJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

java {
    withSourcesJar()
}

val dokkaHtml by tasks.getting(org.jetbrains.dokka.gradle.DokkaTask::class)

// Not sure why, but the Java protobuf files are included twice.
// https://github.com/gradle/gradle/issues/17236
val javadocJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    dependsOn(dokkaHtml)
    archiveClassifier.set("javadoc")
    from(dokkaHtml.outputDirectory)
}

// https://youtrack.jetbrains.com/issue/IDEA-209418
sourceSets.main {
    java.srcDir("build/generated/source/proto/main/java")
    kotlin.srcDir("build/generated/source/proto/main/kotlin")
}

protobuf {
    protoc {
        // The artifact spec for the Protobuf Compiler
        artifact = "com.google.protobuf:protoc:3.22.2"
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                id("kotlin")
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("catalyst-core") {
            artifact(javadocJar)
            from(components["java"])

            pom {
                name.set("Catalyst Core Client")
                description.set("Base client required for using Catalyst on JVM languages.")
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
    sign(publishing.publications["catalyst-core"])
}