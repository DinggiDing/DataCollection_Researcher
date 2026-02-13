# DataCollection uijson renderer 공용화(Desktop 재사용) - 실행 가능한 Plan

현재 상황
- `DataCollection` 프로젝트의 uijson renderer 코드가 `:app` 모듈 내부(`app/src/main/...`)에 위치
- `DataCollection_Researcher`는 Compose **Desktop(JVM)** 기반
- `:app` / `:ui`는 AGP(Android plugin) 모듈이라 Desktop에서 직접 의존 불가

따라서 "공용 renderer 재사용"을 하려면 renderer를 **공용 JVM/KMP 모듈로 분리**해야 합니다.

---

## 목표(Phase A)
- DataCollection renderer/components/blocks 중 **Desktop에서도 컴파일 가능한 subset**을 공용 모듈로 분리
- Researcher Builder Preview에서 그 모듈을 의존해 렌더링을 교체/비교할 수 있게 함

성공 기준
- DataCollection 쪽 신규 모듈 `:uijson-renderer-core`가 `./gradlew :uijson-renderer-core:test` 또는 `:compileKotlin` 통과
- Researcher에서 composite build로 core 모듈을 의존하고 `:composeApp:jvmTest` 통과

---

## 1) DataCollection에 신규 모듈 추가
### 1.1 settings.gradle(.kts)
```kotlin
include(":uijson-renderer-core")
```

### 1.2 uijson-renderer-core/build.gradle.kts (kotlin-jvm)
> AGP가 아닌 순수 JVM 모듈로 시작합니다.

```kotlin
plugins {
  kotlin("jvm") version libs.versions.kotlin.get()
  kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

  // Compose Multiplatform (Desktop용)
  implementation(compose.runtime)
  implementation(compose.foundation)
  implementation(compose.material3)
}
```

⚠️ 주의
- AndroidX Compose BOM 의존성은 Desktop에서 바로 호환되지 않을 수 있습니다.
- Desktop에서 쓰려면 JetBrains Compose Multiplatform(Researcher가 이미 사용 중)로 맞추는 게 안전합니다.

---

## 2) 코드를 모듈로 이동/정리
대상 패키지(현재 위치)
- `com.hdil.datacollection.uijson.v1.render.components`
- `com.hdil.datacollection.uijson.v1.render.blocks`

### 2.1 1차로 옮길 수 있는 후보(일단 컴파일 타겟)
- `SpacerComponent`, `RowComponent`, `ColumnComponent`, `TextFieldComponent`, `ToggleComponent` 등

### 2.2 바로 막히는 대표 케이스
- `android.graphics.Color` 같은 Android 전용 API 사용(예: TextComponent의 parseColor)

해결 방법
- 색 파싱을 순수 Kotlin으로 교체(ARGB hex 파싱)
- 또는 색 파싱을 외부 주입(`ColorParser` 인터페이스)으로 분리

---

## 3) Researcher에 연결(composite build)
Researcher는 이미 `settings.gradle.kts`에 다음을 추가한 상태:
```kotlin
includeBuild("../DataCollection")
```

이제 Researcher의 `composeApp/build.gradle.kts`에서
- `implementation(project(":uijson-renderer-core"))` 형태로는 직접 의존이 안 될 수 있고(빌드가 다름)
- 일반적으로는 포함된 build의 publication(또는 included build dependency substitution)이 필요합니다.

가장 단순한 방법(권장)
- DataCollection core 모듈을 **mavenLocal()**에 publish(snapshots)하거나,
- DataCollection core 모듈을 아예 **git submodule** 또는 **copy module**로 Researcher에 포함

현 단계(MVP)에서는 "Researcher에도 동일한 모듈을 하나 더 생성"하고 소스 공유(복제)로 빠르게 검증한 뒤,
추후 publish/substitution으로 정식 연결을 추천합니다.

---

## 4) Builder Preview에서 renderer 교체
Researcher 쪽에서는 이미 DC-like(모사) 컴포넌트를 만들어서 Desktop UX를 검증하고 있습니다.

다음 단계는:
- `UiRenderer` 인터페이스 추가
- `BuiltInRenderer` / `DcLikeRenderer` / `DataCollectionCoreRenderer`를 토글

---

## 현실적인 MVP 제안
1) 지금처럼 Researcher에서 DC-like 컴포넌트로 UX/props를 먼저 고정
2) DataCollection에서 공용 core 모듈을 만들 때, 고정된 props 스키마에 맞춰 renderer를 이식
3) 최종적으로 Researcher Preview에서 core renderer를 붙여 동일 렌더링 보장

