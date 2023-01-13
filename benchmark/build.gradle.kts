import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsExec

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
    application
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

application {
    mainClass.set("com.monkopedia.hauler.benchmark.MainKt")
}

kotlin {
    js(IR) {
        nodejs {
        }
        this.binaries.target
        useCommonJs()
        binaries.executable()
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
        api("com.monkopedia.ksrpc:ksrpc-sockets:0.7.1")
        api("com.monkopedia.ksrpc:ksrpc-ktor-client:0.7.1")
        api("com.monkopedia.ksrpc:ksrpc-ktor-websocket-client:0.7.1")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.3.3")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
        implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
        compileOnly("io.ktor:ktor-client-core:2.0.2")
        compileOnly("io.ktor:ktor-client-websockets:2.0.2")
        implementation(kotlin("stdlib"))
        api(project(":hauler"))
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
        api("com.monkopedia.ksrpc:ksrpc-sockets:0.7.1")

        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.0.0")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
        implementation("com.github.ajalt:clikt:2.8.0")
        implementation(kotlin("test-junit"))
        implementation("ch.qos.logback:logback-classic:1.2.3")
    }
    sourceSets["jsMain"].dependencies {
        api("com.monkopedia.ksrpc:ksrpc-sockets:0.7.1")
        compileOnly("io.ktor:ktor-client-core:2.0.2")
        compileOnly("io.ktor:ktor-client-js:2.0.2")
        implementation("org.jetbrains.kotlinx:kotlinx-nodejs:0.0.7")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += "-Xskip-prerelease-check"
        freeCompilerArgs += "-Xno-param-assertions"
    }
}

tasks.register("runBenchmark", JavaExec::class) {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.monkopedia.hauler.benchmark.MainKt")
    val jsTask = tasks["jsNodeProductionRun"] as NodeJsExec
    val runArgs = mutableListOf<String>()
    runArgs.add(jsTask.executable ?: error("No executable?"))
    runArgs.add("-i")
    runArgs.addAll(jsTask.nodeArgs)
    if (jsTask.inputFileProperty.isPresent) {
        runArgs.add(jsTask.inputFileProperty.asFile.get().canonicalPath)
    }
    jsTask.args?.let { runArgs.addAll(it) }
    runArgs.add(0, runArgs.size.toString())

    jsTask.dependsOn.forEach {
        dependsOn(it)
    }

    val nativeTask = tasks["runDebugExecutableNative"] as Exec
//    val nativeTask = tasks["runReleaseExecutableNative"] as Exec
    runArgs.add(nativeTask.executable ?: error("No executable?"))
    nativeTask.args?.let { runArgs.addAll(it) }
    nativeTask.dependsOn.forEach {
        dependsOn(it)
    }
    args = runArgs
}

afterEvaluate {

//    val task = tasks["jsNodeProductionRun"] as NodeJsExec
//    val newArgs = mutableListOf<String>()
//    newArgs.addAll(task.nodeArgs)
//    if (task.inputFileProperty.isPresent) {
//        newArgs.add(task.inputFileProperty.asFile.get().canonicalPath)
//    }
//    task.args?.let { newArgs.addAll(it) }
//    println("Args: ${task.executable} $newArgs")
    println("Task: ${tasks["runReleaseExecutableNative"]}")
    println("Task class: ${tasks["runReleaseExecutableNative"]::class}")
}
