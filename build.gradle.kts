plugins {
    java
    id("io.quarkus")
    id("io.freefair.lombok")
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
    implementation("org.mapstruct:mapstruct:${mapstructVersion}")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:${lombokMapstructBindingVersion}")
    annotationProcessor("org.mapstruct:mapstruct-processor:${mapstructVersion}")
    testImplementation("io.quarkus:quarkus-junit")
    testImplementation("io.quarkus:quarkus-jacoco")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.wiremock:wiremock-standalone:${wiremockVersion}")
    testAnnotationProcessor("org.mapstruct:mapstruct-processor:${mapstructVersion}")
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
