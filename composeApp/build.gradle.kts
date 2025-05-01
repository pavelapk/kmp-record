import config.Config
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import plugins.setupKmpTargets

plugins {
    id("android-application-setup")
    id("detekt-setup")
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrainsCompose)
}

android {
    namespace = Config.applicationId + ".sample"
}

kotlin {
    setupKmpTargets(
        onBinariesFramework = {
            it.baseName = "ComposeApp"
            it.isStatic = true
        }
    )

    sourceSets {
        val desktopMain by getting

        androidMain.dependencies {
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
        }

        commonMain.dependencies {
            implementation(projects.recordCore)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenModel)
            implementation(libs.voyager.koin)
            implementation(libs.materialKolor)
//            implementation(libs.mokoPermissions)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlin.coroutines.swing)
        }
    }
}

android {
    dependencies {
        debugImplementation(libs.compose.ui.tooling)
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "dev.theolm.record.sample"
            packageVersion = "1.0.0"

            jvmArgs(
                "-Dapple.awt.application.appearance=system"
            )
        }
    }
}
