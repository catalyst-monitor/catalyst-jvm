import com.google.protobuf.gradle.id

plugins {
    kotlin("jvm")
    id("com.google.protobuf") version "0.9.4"
    `java-library`
    `maven-publish`
    signing
}

group = "com.catalystmonitor.client"
version = "0.1.1"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    runtimeOnly("io.grpc:grpc-netty-shaded:1.66.0")
    implementation(project(":catalyst-core"))
    implementation("io.grpc:grpc-protobuf:1.66.0")
    implementation("io.grpc:grpc-stub:1.66.0")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")

    testImplementation("com.google.protobuf:protobuf-java:3.22.2")
    testImplementation("com.google.protobuf:protobuf-kotlin:3.22.2")
    testImplementation("io.grpc:grpc-testing:1.66.0")
    testImplementation("io.grpc:grpc-inprocess:1.66.0")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation(platform("io.opentelemetry:opentelemetry-bom:1.41.0"))
    testImplementation("io.opentelemetry:opentelemetry-sdk")
    testImplementation("io.opentelemetry:opentelemetry-api")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
    testImplementation("io.opentelemetry.semconv:opentelemetry-semconv:1.26.0-alpha")
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

protobuf {
    protoc {
        // The artifact spec for the Protobuf Compiler
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.66.0"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc") { }
            }
            it.builtins {
                id("kotlin")
            }
        }
    }
}

val dokkaHtml by tasks.getting(org.jetbrains.dokka.gradle.DokkaTask::class)

val javadocJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    dependsOn(dokkaHtml)
    archiveClassifier.set("javadoc")
    from(dokkaHtml.outputDirectory)
}

publishing {
    publications {
        create<MavenPublication>("catalyst-grpc") {
            artifact(javadocJar)
            from(components["java"])

            pom {
                name.set("Catalyst gRPC Client")
                description.set("Monitors gRPC servers with Catalyst.")
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
    sign(publishing.publications["catalyst-grpc"])
}