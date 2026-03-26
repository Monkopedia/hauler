/*
 * Copyright (C) 2026 Jason Monk <monkopedia@gmail.com>
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
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.benchmark)
    alias(libs.plugins.kotlin.allopen)
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
    annotation("kotlinx.benchmark.State")
}

kotlin {
    js(IR) {
        nodejs {}
    }
    jvmToolchain(21)
    jvm {
    }

    applyDefaultHierarchyTemplate()

    sourceSets["commonMain"].dependencies {
        implementation(libs.kotlinx.benchmark.runtime)
        implementation(libs.kotlinx.serialization)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.kotlinx.coroutines)
        implementation(libs.kotlinx.datetime)
        implementation(kotlin("stdlib"))
        implementation(project(":hauler"))
    }
    sourceSets["jvmMain"].dependencies {
        implementation(libs.ksrpc)
        implementation(libs.ksrpc.sockets)
    }

    compilerOptions {
        freeCompilerArgs.addAll("-Xskip-prerelease-check")
    }
}

benchmark {
    configurations {
        named("main") {
            warmups = 2
            iterations = 3
            iterationTime = 3
            iterationTimeUnit = "s"
        }
    }
    targets {
        register("jvm")
        register("js")
    }
}

// Disable ktlint on benchmark-generated source sets
afterEvaluate {
    tasks.matching { it.name.contains("ktlint", ignoreCase = true) }.configureEach {
        enabled = false
    }
}
