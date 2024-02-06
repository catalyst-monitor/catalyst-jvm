import com.google.protobuf.gradle.id

plugins {
    kotlin("jvm")
    id("com.google.protobuf") version "0.9.4"
    `java-library`
    `maven-publish`
}

group = "com.catalystmonitor.client"
version = "0.0.1-SNAPSHOT"

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
            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "myRepo"
            url = uri("file:///home/bill/Work/myRepo")
        }
    }
}