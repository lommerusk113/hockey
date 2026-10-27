plugins {
    groovy
    id("io.micronaut.application") version "5.0.2"
    id("com.gradleup.shadow") version "9.4.1"
}

version = "0.1"
group = "no.hockey"

repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor("io.micronaut.data:micronaut-data-processor")
    annotationProcessor("io.micronaut:micronaut-http-validation")
    annotationProcessor("io.micronaut.security:micronaut-security-processor")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    annotationProcessor("io.micronaut.validation:micronaut-validation-processor")
    implementation("io.micronaut.data:micronaut-data-jdbc")
    // Needed by the Google OpenID client (discovery and token exchange); without it the OAuth routes are silently skipped
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut.flyway:micronaut-flyway")
    implementation("io.micronaut.security:micronaut-security-jwt")
    implementation("io.micronaut.security:micronaut-security-oauth2")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.projectreactor:reactor-core")
    implementation("nl.martijndwars:web-push:5.1.1") {
        // Only the Apache-client PushService is used; async-http-client drags in an old Netty that clashes with Micronaut's
        exclude(group = "org.asynchttpclient")
        exclude(group = "com.beust")
    }
    implementation("org.bouncycastle:bcprov-jdk18on:1.82")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("io.micronaut.validation:micronaut-validation")
    implementation("jakarta.validation:jakarta.validation-api")
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
}

application {
    mainClass = "no.hockey.Application"
}

java {
    sourceCompatibility = JavaVersion.toVersion("25")
    targetCompatibility = JavaVersion.toVersion("25")
}

micronaut {
    runtime("netty")
    testRuntime("spock2")
    processing {
        incremental(true)
        annotations("no.hockey.*")
    }
}
