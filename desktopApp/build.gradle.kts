import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.worxbend.zephyr.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.AppImage, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "com.worxbend.zephyr"
            packageVersion = providers.gradleProperty("zephyrVersion").get()
            description = "A desktop GUI for SDKMAN"
            vendor = "Worxbend"
            licenseFile.set(rootProject.file("LICENSE"))
            linux {
                iconFile.set(rootProject.file("packaging/linux/com.worxbend.zephyr.png"))
                debMaintainer = "balyszyn@gmail.com"
                appCategory = "Utility"
                rpmLicenseType = "MIT"
            }
        }
    }
}
