pluginManagement {
    val quarkusPluginVersion: String by settings
    val quarkusPluginId: String by settings
    val lombokPluginVersion: String by settings
    val errorpronePluginVersion: String by settings
    val nullawayPluginVersion: String by settings
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
    plugins {
        id(quarkusPluginId) version quarkusPluginVersion
        id("io.freefair.lombok") version lombokPluginVersion
        id("net.ltgt.errorprone") version errorpronePluginVersion
        id("net.ltgt.nullaway") version nullawayPluginVersion
    }
}
rootProject.name="quarkus-template"
