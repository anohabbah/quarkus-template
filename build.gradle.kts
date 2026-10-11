import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway

plugins {
    java
    id("io.quarkus")
    id("io.freefair.lombok")
    id("net.ltgt.errorprone")
    id("net.ltgt.nullaway")
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project
val mapstructVersion: String by project
val lombokMapstructBindingVersion: String by project
val wiremockVersion: String by project
val errorproneVersion: String by project
val nullawayVersion: String by project
val bootuiVersion: String by project

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-qute")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-hibernate-orm-panache")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("io.quarkus:quarkus-rest-client-jackson")
    implementation("io.quarkus:quarkus-oidc-client")
    implementation("io.quarkus:quarkus-rest-client-oidc-filter")
    implementation("io.quarkus:quarkus-smallrye-fault-tolerance")
    implementation("io.quarkus:quarkus-opentelemetry")
    implementation("io.quarkus:quarkus-micrometer-opentelemetry")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-container-image-jib")
    implementation("io.quarkus:quarkus-kubernetes")
    implementation("org.mapstruct:mapstruct:${mapstructVersion}")
    implementation("org.jspecify:jspecify")
    implementation("com.julien-dubois.bootui:bootui-quarkus:${bootuiVersion}")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:${lombokMapstructBindingVersion}")
    annotationProcessor("org.mapstruct:mapstruct-processor:${mapstructVersion}")
    testImplementation("io.quarkus:quarkus-junit")
    testImplementation("io.quarkus:quarkus-jacoco")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.wiremock:wiremock-standalone:${wiremockVersion}")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
    testImplementation("org.awaitility:awaitility")
    testAnnotationProcessor("org.mapstruct:mapstruct-processor:${mapstructVersion}")
    errorprone("com.google.errorprone:error_prone_core:${errorproneVersion}")
    errorprone("com.uber.nullaway:nullaway:${nullawayVersion}")
}

group = "dev.abbah"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
    options.compilerArgs.add("-Amapstruct.defaultComponentModel=cdi")
}

nullaway {
    onlyNullMarked = true
    jspecifyMode = true
}

tasks.withType<JavaCompile>().configureEach {
    val isMainCompile = name == "compileJava"
    options.errorprone {
        enabled = isMainCompile
        disableAllChecks = true
        error("RequireExplicitNullMarking")
        nullaway {
            error()
            treatGeneratedAsUnannotated = true
        }
    }
}
