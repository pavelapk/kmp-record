import com.vanniktech.maven.publish.SonatypeHost
import config.Config
import plugins.setupKmpTargets

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("android-lib-setup")
    id("detekt-setup")
    id("publish-setup")
}

android {
    namespace = Config.applicationId
}

kotlin {
    explicitApi()
    setupKmpTargets()

    sourceSets {

        androidMain.dependencies {
            implementation(libs.androidx.startup)
            implementation(libs.androidx.core.ktx)
        }
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlin.test.common)
            implementation(libs.kotlin.test.annotation)
            implementation(libs.kotlinx.coroutines.test)
        }
        val desktopMain by getting {
            val jcvVersion      = "1.5.11"
            val ffmpegPresetVer = "7.1-$jcvVersion"

            dependencies {
                // Java wrapper – but without its heavy transitive deps
                implementation("org.bytedeco:javacv:$jcvVersion") {
                    isTransitive = false          // <- no OpenCV, no OpenBLAS …
                }

                // JNI glue + FFmpeg preset jars (both light)
                implementation("org.bytedeco:javacpp:$jcvVersion")
                implementation("org.bytedeco:ffmpeg:$ffmpegPresetVer")

                /* ---------- native binaries (runtime-only!) ---------- */
                runtimeOnly("org.bytedeco:ffmpeg:$ffmpegPresetVer:windows-x86_64")
                // runtimeOnly("org.bytedeco:ffmpeg:$ffmpegPresetVer:windows-arm64")
                runtimeOnly("org.bytedeco:ffmpeg:$ffmpegPresetVer:macosx-x86_64")
                runtimeOnly("org.bytedeco:ffmpeg:$ffmpegPresetVer:macosx-arm64")
                runtimeOnly("org.bytedeco:ffmpeg:$ffmpegPresetVer:linux-x86_64")
                runtimeOnly("org.bytedeco:ffmpeg:$ffmpegPresetVer:linux-arm64")
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    // signAllPublications()

    val version = System.getenv("VERSION") ?: Config.libVersion
    coordinates(
        groupId = Config.groupId,
        artifactId = Config.artifactId + "-core",
        version = version
    )

    pom {
        name.set("KMP-Record")
        description.set("Simple library to record audio on Android and iOS")
    }
}