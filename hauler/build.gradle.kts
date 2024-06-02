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
import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.serialization)
    java
    id("com.monkopedia.ksrpc.plugin")
    alias(libs.plugins.dokka)
    id("org.gradle.maven-publish")
    id("org.gradle.signing")
}

group = "com.monkopedia"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    js(IR) {
        browser {}
    }
    jvm {
        withJava()
    }

    macosX64 {
        binaries {}
    }
    macosArm64 {
        binaries {}
    }
    iosArm64 {
        binaries {}
    }
    iosSimulatorArm64 {
        binaries {}
    }

    val hostOs = System.getProperty("os.name")
    if (hostOs == "Linux") {
        linuxX64 {
            binaries {}
        }
        linuxArm64 {
            binaries {}
        }
    }
    mingwX64 {
        binaries {}
    }
    applyDefaultHierarchyTemplate()


    sourceSets["commonMain"].dependencies {
        api(libs.ksrpc)
        implementation(libs.kotlinx.serialization)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.kotlinx.coroutines)
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
        implementation(libs.kotlinx.serialization)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.clikt)
        implementation("ch.qos.logback:logback-classic:1.2.3")
    }
    sourceSets["jsMain"].dependencies {
        api(libs.ktor.client)
        api(libs.ktor.client.js)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += "-Xskip-prerelease-check"
        freeCompilerArgs += "-Xno-param-assertions"
    }
}

val dokkaJavadoc = tasks.create("dokkaJavadocCustom", DokkaTask::class) {
    project.dependencies {
        plugins("org.jetbrains.dokka:kotlin-as-java-plugin:1.9.0")
    }
    // outputFormat = "javadoc"
    outputDirectory.set(File(project.buildDir, "javadoc"))
    inputs.dir("src/commonMain/kotlin")
}

val javadocJar = tasks.create("javadocJar", Jar::class) {
    dependsOn(dokkaJavadoc)
    archiveClassifier.set("javadoc")
    from(File(project.buildDir, "javadoc"))
}

publishing {
    publications.all {
        if (this !is MavenPublication) return@all
        artifact(javadocJar)
        pom {
            name.set(project.name)
            description.set("A tool for logging over rpcs")
            url.set("http://www.github.com/Monkopedia/hauler")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
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
                url.set("http://github.com/Monkopedia/hauler/")
            }
        }
    }
    repositories {
        maven(url = "https://oss.sonatype.org/service/local/staging/deploy/maven2/") {
            name = "OSSRH"
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}

afterEvaluate {
    tasks.withType(org.gradle.plugins.signing.Sign::class) {
        val signingTask = this
        tasks.withType(org.gradle.api.publish.maven.tasks.AbstractPublishToMaven::class) {
            dependsOn(signingTask)
        }
    }
}
