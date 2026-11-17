// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

plugins {
    kotlin("multiplatform")
}

// A Swing debugger for any 6502 machine. It knows the CPU (:cpu) and nothing
// else: a machine plugs in through DebugSession and DebugTarget, and passes in
// its own names for addresses. JVM-only, like :desktop.
kotlin {
    jvm {
        // Java 17 bytecode and API, whichever JDK runs the build, so the jar runs on 17+.
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xjdk-release=17")
        }
    }

    sourceSets {
        jvmMain.dependencies { api(project(":cpu")) }
        jvmTest.dependencies { implementation(kotlin("test")) }
    }
}
