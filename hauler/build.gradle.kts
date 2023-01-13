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
plugins {
    java
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.monkopedia.ksrpc.plugin")
}

repositories {
    jcenter()
    mavenCentral()
    mavenLocal()
    maven(url = "https://dl.bintray.com/kotlin/kotlin-dev/")
    maven(url = "https://dl.bintray.com/kotlin/kotlin-eap/")
    maven(url = "https://kotlinx.bintray.com/kotlinx/")
}

kotlin {
    js(IR) {
        browser {}
    }
    jvm {
        withJava()
    }
    // Determine host preset.
    val hostOs = System.getProperty("os.name")

    // Create target for the host platform.
    val hostTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        hostOs.startsWith("Windows") -> mingwX64("native")
        else -> throw GradleException(
            "Host OS '$hostOs' is not supported in Kotlin/Native $project."
        )
    }
    hostTarget.apply {
        binaries {
            executable()
        }
    }
    sourceSets["commonMain"].dependencies {
        api("com.monkopedia.ksrpc:ksrpc-core:0.7.1")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.3.3")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
        implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
        implementation(kotlin("stdlib"))
        compileOnly("io.ktor:ktor-io:2.0.2")
    }
    sourceSets["jvmMain"].dependencies {
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
        implementation("org.slf4j:slf4j-api:2.0.6")
        compileOnly("io.ktor:ktor-server-core:2.0.2")
        compileOnly("io.ktor:ktor-server-host-common:2.0.2")
        compileOnly("io.ktor:ktor-server-netty:2.0.2")
        compileOnly("io.ktor:ktor-client-core:2.0.2")

        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.0.0")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0")
        implementation("com.github.ajalt:clikt:2.8.0")
        implementation("ch.qos.logback:logback-classic:1.2.3")
    }
    sourceSets["jsMain"].dependencies {
        compileOnly("io.ktor:ktor-client-core:2.0.2")
        compileOnly("io.ktor:ktor-client-js:2.0.2")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += "-Xskip-prerelease-check"
        freeCompilerArgs += "-Xno-param-assertions"
    }
}
