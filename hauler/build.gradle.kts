/*
 * Copyright 2022 Jason Monk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.serialization)
    id("com.monkopedia.ksrpc.plugin")
    alias(libs.plugins.vannik.publish)
    `signing`
}

group = "com.monkopedia"

kotlin {
    js(IR) {
        nodejs()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }
    jvmToolchain(11)
    jvm {
    }

    macosX64()
    macosArm64()
    iosArm64()
    iosX64()
    iosSimulatorArm64()

    linuxX64()
    linuxArm64()
    mingwX64()
    applyDefaultHierarchyTemplate()

    sourceSets["commonMain"].dependencies {
        api(libs.ksrpc)
        implementation(libs.kotlinx.serialization)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.kotlinx.coroutines)
        implementation(libs.kotlinx.atomicfu)
        implementation(libs.kotlinx.datetime)
        implementation(kotlin("stdlib"))
        compileOnly(libs.ktor.io)
    }
    sourceSets["jvmMain"].dependencies {
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
        implementation(libs.slf4j.api)
        compileOnly(libs.ktor.server)
        compileOnly(libs.ktor.server.host.common)
        compileOnly(libs.ktor.server.netty)
        compileOnly(libs.ktor.client)
        implementation(libs.clikt)
        implementation(libs.logback.classic)
    }
    sourceSets["commonTest"].dependencies {
        implementation(kotlin("test"))
        implementation(libs.kotlinx.coroutines.test)
    }
    sourceSets["jsMain"].dependencies {
        api(libs.ktor.client)
        api(libs.ktor.client.js)
    }

    compilerOptions {
        freeCompilerArgs.addAll("-Xskip-prerelease-check")
    }
}

mavenPublishing {
    pom {
        name.set(project.name)
        description.set("A tool for logging over rpcs")
        url.set("https://www.github.com/Monkopedia/hauler")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("monkopedia")
                name.set("Jason Monk")
                email.set("monkopedia@gmail.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/Monkopedia/hauler.git")
            developerConnection.set("scm:git:ssh://github.com/Monkopedia/hauler.git")
            url.set("https://github.com/Monkopedia/hauler/")
        }
    }
    publishToMavenCentral()
    signAllPublications()
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}
