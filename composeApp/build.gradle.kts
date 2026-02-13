import org.gradle.api.tasks.JavaExec
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

kotlin {
    jvm {
        @Suppress("OPT_IN_USAGE")
        mainRun {
            mainClass.set("com.hdil.datacollection_researcher.MainKt")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutinesSwing)

                implementation(compose.material)
                implementation(compose.materialIconsExtended)

                // Phase 3: Firestore Export (JVM)
                implementation("com.google.firebase:firebase-admin:9.5.0")

                // Phase 5: Excel 생성을 위한 Apache POI
                api("org.apache.poi:poi-ooxml:5.4.0")

                // NOTE: androidx.compose.*-android/*-jvmstubs/*-desktop 아티팩트는
                // Compose Multiplatform(Desktop)에서는 불필요하며, 오히려 리졸브 실패를 유발할 수 있습니다.
                // (특히 foundation-layout-android)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.hdil.datacollection_researcher.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.hdil.datacollection_researcher"
            packageVersion = "1.0.0"
        }
    }
}
