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
    alias(libs.plugins.ksrpc)
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
        api(libs.ksrpc)
        api(libs.ksrpc.sockets)
        api(libs.ksrpc.ktor.client)
        api(libs.ksrpc.ktor.websocket.client)
        implementation(libs.kotlinx.serialization)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.kotlinx.coroutines)
        implementation(libs.kotlinx.datetime)
        compileOnly(libs.ktor.client)
        compileOnly(libs.ktor.client.websockets)
        implementation(kotlin("stdlib"))
        api(project(":hauler"))
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
        compileOnly(libs.ksrpc.sockets)
        implementation(libs.kotlinx.serialization)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.kotlinx.coroutines)
        implementation(libs.clikt)
        implementation(kotlin("test-junit"))
        implementation("ch.qos.logback:logback-classic:1.2.3")
    }
    sourceSets["jsMain"].dependencies {
        api(libs.ksrpc.sockets)
        api(libs.ktor.client)
        api(libs.ktor.client.js)
        implementation(libs.kotlinx.nodejs)
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
