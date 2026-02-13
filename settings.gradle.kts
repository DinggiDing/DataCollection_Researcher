rootProject.name = "DataCollection_Researcher"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://plugins.gradle.org/m2/") }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":composeApp")

// DataCollection(별도 프로젝트)와 composite build로 연결해서 renderer/ui 모듈을 재사용합니다.
// 경로가 다르면 이 줄만 맞게 조정하면 됩니다.
includeBuild("../DataCollection")
