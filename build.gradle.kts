buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.monkopedia.ksrpc:ksrpc-gradle-plugin:${libs.versions.ksrpc.get()}")
    }
}

plugins {
    alias(libs.plugins.jlleitschuh.ktlint)
    alias(libs.plugins.hierynomus.license)
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.binary.compatibility.validator)
}

group = "com.monkopedia"

apiValidation {
    ignoredProjects += listOf("benchmark", "microbenchmark")
}

repositories {
    mavenLocal()
    mavenCentral()
}

val ktlintCoreVersion = libs.versions.ktlint.formatter.get()

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
            com.hierynomus.gradle.license.tasks.LicenseCheck::class,
        ) {
            source =
                fileTree(project.projectDir) {
                    include("**/*.kt")
                    exclude("build/**")
                }
        }
        tasks["license"].dependsOn("licenseCheckForKotlin")
        tasks.register(
            "licenseFormatForKotlin",
            com.hierynomus.gradle.license.tasks.LicenseFormat::class,
        ) {
            source =
                fileTree(project.projectDir) {
                    include("**/*.kt")
                    exclude("build/**")
                }
        }
        tasks["licenseFormat"].dependsOn("licenseFormatForKotlin")

        this.license {
            header = rootProject.file("license-header.txt")
            includes(listOf("**/*.kt"))
            strictCheck = true
            mapping("kt", "SLASHSTAR_STYLE")
            ext["year"] =
                java.util.Calendar
                    .getInstance()
                    .get(java.util.Calendar.YEAR)
            ext["name"] = "Jason Monk"
            ext["email"] = "monkopedia@gmail.com"
        }

        configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            version.set(ktlintCoreVersion)
            android.set(true)
        }
    }
}
