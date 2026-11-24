// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

plugins {
    kotlin("multiplatform")
}

// Cross-development for the Electron: a BBC BASIC tokeniser, a 6502 assembler
// in the BASIC inline assembler's syntax, and a writer for UEF tapes the OS can
// load. It knows the CPU's opcodes (:cpu) and nothing of the emulator; the tests
// use :electron to read back the tapes it writes.
kotlin {
    jvm {
        // Java 17 bytecode and API, whichever JDK runs the build, so the jar runs on 17+.
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xjdk-release=17")
        }
    }

    sourceSets {
        commonMain.dependencies { implementation(project(":cpu")) }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":electron"))
        }
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("elkslop.romDirectory", rootProject.file("roms").absolutePath)
    inputs.file(rootProject.file("roms/basic.rom")).withPropertyName("basicRom")
    testLogging { events("passed", "skipped", "failed") }
}

val mainCompilation = kotlin.jvm().compilations.getByName("main")

fun JavaExec.devkitCommand() {
    mainClass.set("elkslop.devkit.DevkitKt")
    classpath(mainCompilation.output.allOutputs)
    mainCompilation.runtimeDependencyFiles?.let { classpath(it) }
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Run the devkit command line from the project directory. Pass options with --args=\"...\"."
    devkitCommand()
}

// programs/NAME.bas or NAME.asm becomes programs/build/NAME.uef.
tasks.register<JavaExec>("buildPrograms") {
    group = "build"
    description = "Build every program in programs/ into a tape in programs/build."
    devkitCommand()
    val programs = rootProject.file("programs")
    val output = rootProject.file("programs/build")
    inputs.files(fileTree(programs) { include("*.bas", "*.asm") }).withPropertyName("sources")
    outputs.dir(output)
    args("--all", programs.path, "-o", output.path)
}
