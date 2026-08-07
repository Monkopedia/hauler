import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin

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

// Kotlin 2.4.10 defaults the JS/Wasm toolchain's Node.js to 25.0.0, which the transitive
// `nanoid` npm dependency rejects ("engine node incompatible: ^22 || ^24 || >=26"). Pin
// Node to 24.10.0 (satisfies the constraint; the JS toolchain already resolves it) so the
// Wasm npm install succeeds locally and in CI.
plugins.withType<NodeJsPlugin> {
    the<NodeJsEnvSpec>().version.set("24.10.0")
}
plugins.withType<WasmNodeJsPlugin> {
    the<WasmNodeJsEnvSpec>().version.set("24.10.0")
}

apiValidation {
    ignoredProjects += listOf("benchmark", "microbenchmark")

    // BCV's klib (native/JS/Wasm) ABI validation is OFF by default. Without this, `klibApiCheck`
    // is wired into `check` and reports BUILD SUCCESSFUL while every *ApiBuild task is SKIPPED --
    // so 10 of the 11 published platform artifacts had no ABI gate at all. See #21.
    klib {
        enabled = true
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

val ktlintCoreVersion =
    libs.versions.ktlint.formatter
        .get()

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
