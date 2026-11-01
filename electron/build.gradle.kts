// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
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
    inputs.files(rootProject.file("roms/os.rom"), rootProject.file("roms/basic.rom"))
        .withPropertyName("electronRoms")
    testLogging { events("passed", "skipped", "failed") }
}
