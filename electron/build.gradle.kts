// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm {
        // Java 17 bytecode and API, whichever JDK runs the build, so the jar runs on 17+.
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xjdk-release=17")
        }
    }
    macosArm64()
    macosX64()
    linuxArm64()
    linuxX64()
    mingwX64()

    sourceSets {
        commonMain.dependencies { api(project(":cpu")) }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

// ROMs remain local inputs, never resources packaged into the library.
tasks.withType<Test>().configureEach {
    systemProperty("elkslop.romDirectory", rootProject.file("roms").absolutePath)
    systemProperty("elkslop.tapeDirectory", rootProject.file("tapes").absolutePath)
    inputs.files(rootProject.file("roms/os.rom"), rootProject.file("roms/basic.rom"))
        .withPropertyName("electronRoms")
    inputs.dir(rootProject.file("tapes")).withPropertyName("electronTapes")
    testLogging { events("passed", "skipped", "failed") }
}
