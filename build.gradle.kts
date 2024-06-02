buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.monkopedia.ksrpc:ksrpc-gradle-plugin:${libs.versions.ksrpc.get()}")
        classpath("gradle.plugin.com.github.johnrengelman:shadow:7.1.2")
    }
}

plugins {
    id("org.jlleitschuh.gradle.ktlint") version "11.0.0"
    id("com.github.hierynomus.license") version "0.16.1"
    id("com.monkopedia.ksrpc.plugin") version libs.versions.ksrpc.get() apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

group = "com.monkopedia"

repositories {
    mavenLocal()
    mavenCentral()
}

subprojects {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    afterEvaluate {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        apply(plugin = "com.github.hierynomus.license")
        tasks.register(
            "licenseCheckForKotlin",
            com.hierynomus.gradle.license.tasks.LicenseCheck::class
        ) {
            source = fileTree(project.projectDir) { include("**/*.kt") }
        }
        tasks["license"].dependsOn("licenseCheckForKotlin")
        tasks.register(
            "licenseFormatForKotlin",
            com.hierynomus.gradle.license.tasks.LicenseFormat::class
        ) {
            source = fileTree(project.projectDir) { include("**/*.kt") }
        }
        tasks["licenseFormat"].dependsOn("licenseFormatForKotlin")

        this.license {
            header = rootProject.file("license-header.txt")
            includes(listOf("**/*.kt"))
            strictCheck = true
            ext["year"] = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            ext["name"] = "Jason Monk"
            ext["email"] = "monkopedia@gmail.com"
        }

        configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            version.set("0.48.0")
            android.set(true)
        }
    }
}
