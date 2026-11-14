# GitHub Copilot Instructions — Flow Mobile

## 1. Project Context & Stack

This repository contains the mobile client for the **Flow** social network, built on **Kotlin Multiplatform (KMP)** and **Compose Multiplatform (CMP)** for Android and iOS.

### Core Stack & Validated Versions (`libs.versions.toml`)
- **Kotlin**: `2.3.10`
- **Compose Multiplatform**: `1.10.1` (Android + iOS via Skia/Metal)
- **Navigation**: Navigation Compose KMP `2.9.0` (type-safe `@Serializable` routes)
- **Local Database**: Room KMP `2.7.1` (`BundledSQLiteDriver`, SQLite `2.5.0-alpha11`)
- **Networking**: Ktor Client `3.1.2` (`CIO` engine for Android, `Darwin` engine for iOS)
- **Dependency Injection**: Koin `4.0.0-RC1` (`koin-core` + `koin-compose`)
- **Image & Media Loading**: Coil 3 `3.4.0` (`coil-compose` + `coil-network-ktor3`)
- **Concurrency & Serialization**: Kotlinx Coroutines `1.10.2`, Kotlinx Serialization `1.8.1`
- **Build Toolchain**: Android Gradle Plugin `9.2.1`, Gradle Wrapper `9.4.1`, KSP `2.3.4`

### Target Platforms
- **Android**: `minSdk 28`, `compileSdk 35`, `targetSdk 35`
- **iOS**: iOS 16.4+ (`iosArm64` for physical devices, `iosSimulatorArm64` for Apple Silicon simulators; Intel `iosX64` is removed)

---

## 2. Fundamental Architectural Rules

### Rule 1: `commonMain` is the Default
Everything that does not strictly require platform-specific OS APIs must live in `commonMain`:
- All UI screens, components, themes, animations, and typography
- Navigation routes and backstack handling
- State management and ViewModel / Screen coordinators
- Business logic, offline sync, and durable outbox queues
- Ktor API clients, WebSocket realtime transports, and DTO parsing
- Room KMP database, DAO interfaces, and schema migrations

Do NOT create separate Android and iOS implementations of screens.

### Rule 2: Narrow Native Escape Hatch
Platform-specific code in `androidMain` and `iosMain` is permitted ONLY when interacting with proprietary or hardware APIs:
- Camera recording & preview (CameraX on Android / AVFoundation on iOS)
- Low-latency audio capture & playback (AudioRecord on Android / AVAudioSession on iOS)
- Biometrics & Secure storage (Android Keystore / iOS Keychain Services)
- Motion sensors for Bump detection (SensorManager on Android / CoreMotion on iOS)
- Push token registration (FCM on Android / APNs on iOS)

Native implementations must be as thin as possible. Keep orchestrators and state in `commonMain`.

### Rule 3: Single Module Target (`:shared`)
The mobile application code resides in `Mobile/kotlin/shared/src/commonMain/kotlin/me/floow/`:
- `app/`: Application entry point (`FlowApp.kt`), root NavHost, DI graph modules
- `core/`: Common platform infrastructure (`api/`, `db/`, `realtime/`, `media/`, `auth/`, `uikit/`)
- `features/`: Feature-first domain and presentation packages:
  - `feed/`: Tinder-like card swipe mechanics, ranking, session snapshots
  - `chat/`: Direct Messages v2, typing indicators, read cursors, video circles, voice messages
  - `comments/`: Bottom sheet comments, replies inbox, real-time sync
  - `profile/`: User profile, settings, Bump contact exchange
  - `auth/`: Google OAuth, session refresh, onboarding

Platform runners are thin hosts:
- `:androidApp` (`Mobile/kotlin/app/`)
- `iosApp` (`Mobile/iosApp/`)

### Rule 4: No God Objects & Pragmatic Architecture
- Decompose giant state holders into focused coordinators with clear single responsibilities.
- Avoid introducing unnecessary Clean Architecture layers (UseCase/Interactor/Repository/DataSource) for trivial one-off operations.
- State flow is unidirectional: `User Action -> Coordinator / ViewModel -> StateFlow<UiState> -> Compose UI`.
- Visual correctness is paramount: adhere strictly to Figma tokens, Montserrat typography, and 120 FPS render budgets without unnecessary recompositions.

### Rule 5: Backend Contract Parity
The Go backend API is the source of truth for networking:
- DM v2 uses cursor-based pagination and WebSocket streams with monotonic `seq` and `event_id` replay.
- File uploads use S3 presigned URLs via `POST /api/v1/uploads/presign`.
