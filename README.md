<div align="center">

# 💳 SpendWise — Android Personal Finance & Expense Intelligence

**An offline-first, mathematical-precision Android budgeting application built with Jetpack Compose, Clean Architecture, and behavioral AI intelligence.**

[![SpendWise Android CI](https://github.com/suryacharan945/SpendWise/actions/workflows/android.yml/badge.svg)](https://github.com/suryacharan945/SpendWise/actions/workflows/android.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-purple.svg)](https://kotlinlang.org)
[![Android SDK](https://img.shields.io/badge/Android%20SDK-24--36-green.svg)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue.svg)](https://developer.android.com/jetpack/compose)
[![Tests](https://img.shields.io/badge/Unit%20Tests-93%20Passing-brightgreen.svg)]()
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

<br/>

<img src="docs/screenshots/app_logo.png" alt="SpendWise Logo" width="120" height="120" />

</div>

---

## 📌 1. Project Overview & Problem Statement

Most personal finance apps suffer from three subtle, critical software engineering pitfalls:
1. **Floating-Point Accumulation Drift:** Financial calculations performed using IEEE 754 floating-point primitives (`Double` or `Float`) accumulate rounding errors over time ($0.1 + 0.2 = 0.30000000000000004$), causing ledger discrepancies, failed balance reconciliations, and broken financial audit trails.
2. **Brittle Offline Synchronization:** Many mobile apps either fail completely when disconnected or rely on naive Last-Write-Wins (LWW) mechanisms based solely on client wall-clock timestamps, causing data loss when clocks drift or near-simultaneous offline updates collide.
3. **Reactive Expense Logging:** Typical apps function as passive ledgers rather than active financial coaches, failing to alert users of spending velocity anomalies or overspending risks until month-end statement shock.

**SpendWise** is engineered as a zero-compromise, offline-first personal finance platform. It eliminates floating-point errors entirely via an unboxed sub-unit currency engine, enforces distributed multi-device consistency through monotonic version vectors and soft-delete tombstones, and introduces cognitive behavioral AI coaching.

---

## 🏗️ 2. Clean Architecture & System Design

SpendWise enforces strict directional dependencies following Clean Architecture:  
`Presentation Layer -> Domain Layer <- Data Layer` with cross-cutting `Core` infrastructure.

```
┌────────────────────────────────────────────────────────────────────────┐
│                          PRESENTATION LAYER                            │
│     Jetpack Compose UI  •  MVI/MVVM Flow  •  Material 3 Expressive     │
│   [Dashboard]   [Expenses]   [Goals]   [Analytics]   [AI Coach]        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Observes Resource<T> & Pure Domain Models
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                             DOMAIN LAYER                               │
│              Pure Kotlin (Zero Android Dependencies, 100% Testable)     │
│                                                                        │
│   ┌──────────────────────────┐  ┌────────────────────────────────────┐ │
│   │ Feature Use Cases        │  │ Behavioral AI Engine               │ │
│   │ • AddExpenseUseCase      │  │ • AICoachEngine  • SpendingPredictor│ │
│   │ • GetDashboardDataUseCase│  │ • AnomalyDetector• ToneEngine      │ │
│   │ • DepositToGoalUseCase   │  │ • StreakEngine   • Ranker          │ │
│   │ • SyncDataUseCase        │  │ • NotificationDedup Engine         │ │
│   └───────────┬──────────────┘  └───────────────────┬────────────────┘ │
│               │                                     │                  │
│               └──────────────────┬──────────────────┘                  │
│                                  ▼                                     │
│                     [IFinanceRepository Contracts]                     │
│                     [Money Value Class (Integer Paise)]                │
│                     [SyncableEntity Monotonic Contract]                │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Implemented by Data Layer
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                              DATA LAYER                                │
│   ┌───────────────────────────────────┐ ┌────────────────────────────┐ │
│   │ Local Persistence (Room / SQLite) │ │ Cloud Sync Worker          │ │
│   │ • SQLCipher 256-bit AES           │ │ • Firestore / REST API     │ │
│   │ • Composite Index: (user, date)   │ │ • SyncWorker (WorkManager) │ │
│   │ • Direct SQL Group Aggregations   │ │ • Conflict Resolution Hub  │ │
│   │ • Sub-unit integer paise storage  │ │ • Tombstone Soft-Deletes   │ │
│   └───────────────────────────────────┘ └────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

### Layer Responsibilities
* **Domain Layer (`com.example.domain`):** Pure Kotlin with **zero Android SDK dependencies**. Houses all core business rules, entity models, and use cases.
* **Presentation Layer (`com.example.ui`):** Modern Jetpack Compose UI implementing Unidirectional Data Flow (UDF). ViewModels expose immutable `StateFlow<Resource<ViewState>>` states.
* **Data Layer (`com.example.data`):** Single source of truth managed by Room Database, protected by SQLCipher encryption, with direct SQL-side group aggregations (`totalsBetweenGrouped`) to avoid in-memory deserialization overhead.
* **Core Layer (`com.example.core`):** Google Tink AEAD authenticated symmetric encryption, hardware-backed Android KeyStore integration, and heuristic fraud detection.

---

## 💎 3. Key Implemented Features

### 1. Mathematical Correctness Engine (`Money` Value Class)
All currency values are encapsulated in an unboxed value class representing monetary amounts in **integer paise** (1 Rupee = 100 Paise):
```kotlin
@JvmInline
value class Money(val paise: Long) : Comparable<Money> {
    operator fun plus(other: Money): Money = Money(this.paise + other.paise)
    operator fun minus(other: Money): Money = Money(this.paise - other.paise)
    operator fun times(factor: Double): Money = Money(Math.round(this.paise * factor))
    operator fun div(divisor: Long): Money = Money(this.paise / divisor)
    
    fun rupees(): Double = paise / 100.0
    fun format(): String = String.format(Locale.ENGLISH, "₹%,.2f", rupees())
}
```
* **Zero Heap Allocations:** Compiles directly to primitive `long` in JVM bytecode.
* **Database Purity:** Room stores `amountPaise: Long`, completely avoiding floating-point math in SQLite queries.

### 2. Behavioral AI Financial Coach
* **Weighted Moving Average (WMA) Predictor:** Computes $w = [0.10, 0.20, 0.30, 0.40]$ across a 4-week window to forecast month-end budget depletion before overspending occurs.
* **Degenerate-Guarded Z-Score Anomaly Detector:** Flags unusual high-value expenditures with safeguards preventing false alarms on low-variance baseline datasets ($N \ge 5$).
* **Cognitive Tone Engine:** Adapts financial coach feedback across *Empathetic*, *Direct*, and *Celebratory* tones based on user behavioral feedback while preserving exact numerical recommendations.
* **Multi-Factor Insight Ranker:** Ranks financial tips using an objective composite formula:
  $$\text{Score} = 0.35 \times \text{Urgency} + 0.30 \times \text{Impact} + 0.20 \times \text{Relevance} + 0.15 \times \text{Actionability}$$

### 3. Voice Expense Parser (NLP Tokenization)
* Multi-lingual natural language parser tokenizing conversational inputs (e.g., *"Spent 450 rupees on dinner with friends"*) into structured amounts, categories, and date stamps.

### 4. Distributed Conflict Resolution & Offline Sync
* **Monotonic Version Tie-Breaking:** Resolves multi-device edits with Last-Write-Wins reinforced by `syncVersion` and soft-delete tombstones. Deleted items are never resurrected by delayed sync packets.

### 5. Enterprise Security & Fraud Prevention
* **Hardware-Backed Cryptography:** 256-bit AES master keys sealed within the Android KeyStore / StrongBox.
* **Google Tink AEAD:** Authenticated symmetric encryption for session tokens and sensitive configuration.
* **Fraud Detection Engine:** Heuristic checks detecting rapid transaction velocity (>3 transactions in 60s), nocturnal anomalies (01:00 AM – 05:00 AM), and duplicate payment attempts.

---

## 🛠️ 4. Technology Stack & Dependencies

| Layer / Concern | Technology | Version | Purpose |
| :--- | :--- | :--- | :--- |
| **Language** | Kotlin | 2.2.21 | Primary language with strict null safety |
| **Toolchain** | Gradle | 9.3.1 | Build automation with Kotlin DSL (`.kts`) |
| **Runtime Target** | Android SDK | Min: 24, Target: 36 | Covers Android 7.0 Nougat through Android 15 |
| **UI Framework** | Jetpack Compose | BOM 2024.x | Declarative reactive UI components |
| **Design System** | Material 3 | Expressive | Dynamic Color, Dark/Light mode parity |
| **Persistence** | Room SQLite | 2.6.x | Local single source of truth |
| **Database Encryption** | SQLCipher | 4.5.x | 256-bit AES disk encryption |
| **Asynchronous** | Kotlin Coroutines & Flow | 1.8.x | Reactive streams & background execution |
| **Dependency Injection / KSP** | Google KSP | 2.0.x | High-speed annotation processing |
| **Testing** | JUnit 4, Robolectric, Roborazzi | 4.12+ | JVM unit and snapshot testing |

---

## 🧪 5. Verification & Test Suite

The project includes **93 automated unit tests** across 19 test suites, covering domain use cases, algorithmic precision, cryptographic guarantees, and UI rendering:

```bash
# Execute the full unit test suite locally
./gradlew testDebugUnitTest --no-daemon
```

### Verified Test Suite Breakdown (100% Passing)

| Test Suite Class | Tests | Status | Scope |
| :--- | :---: | :---: | :--- |
| `FintechLaunchChecklistTest` | 9 | **PASS** | `Money` precision, day deduplication, conflict resolution |
| `BehaviorDrivenAssistantTest` | 10 | **PASS** | WMA prediction, Z-score anomaly detector, dynamic ranking |
| `BehavioralNotificationSystemTest` | 9 | **PASS** | Quiet hours filter, digest batching, degenerate variance |
| `NotificationEngineTest` | 5 | **PASS** | Notification deduplication and channel dispatch |
| `AddExpenseUseCaseTest` | 6 | **PASS** | Validation rules, fraud gate, tag normalization |
| `PureAddExpenseUseCaseTest` | 6 | **PASS** | Pure domain business rules and entity instantiation |
| `DepositToGoalUseCaseTest` | 3 | **PASS** | Goal milestone calculations and balance updates |
| `CreateGoalUseCaseTest` | 5 | **PASS** | Savings goal creation, target date validation |
| `SyncConflictResolverTest` | 4 | **PASS** | Monotonic version comparison, clock skew, tombstones |
| `FintechSecurityManagerTest` | 6 | **PASS** | AES-256-GCM encryption, KeyStore decryption, tamper detection |
| `FraudDetectionEngineTest` | 5 | **PASS** | Velocity checks, nocturnal anomalies, duplicate alerts |
| `VoiceExpenseParserTest` | 5 | **PASS** | Natural language tokenization and currency extraction |
| `AuthFlowLogicTest` | 3 | **PASS** | Session validation, biometric fallback, credential guards |
| `FeatureUseCasesTest` | 5 | **PASS** | Expense filtering, category aggregations |
| `FinancialProfileUseCasesTest` | 5 | **PASS** | Income versus expense ratio calculations |
| `UserProfileDetailsUseCaseTest` | 4 | **PASS** | User profile hydration and currency preference handling |
| `ExampleRobolectricTest` | 1 | **PASS** | Android context and string resource verification |
| `ExampleUnitTest` | 1 | **PASS** | JVM arithmetic invariants |
| `GreetingScreenshotTest` | 1 | **PASS** | Roborazzi snapshot verification |
| **TOTAL** | **93** | **100% PASS** | **Runtime: 61.48s** |

---

## 🚀 6. Installation & Build Instructions

### Prerequisites
* **Android Studio:** Ladybug (2024.2.1+) or newer.
* **JDK:** Version 17 or Version 22 (Temurin / Oracle JDK).
* **Android SDK:** Platform 36 and Build-Tools 36.0.0 installed via SDK Manager.

### Local Setup
```bash
# 1. Clone the repository
git clone https://github.com/suryacharan945/SpendWise.git
cd SpendWise

# 2. Configure environment (Optional: for AI Studio capabilities)
cp .env.example .env

# 3. Build debug APK
./gradlew assembleDebug

# 4. Run unit tests
./gradlew testDebugUnitTest
```

The compiled debug APK will be generated at:  
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🔒 7. Security & Privacy

* **Zero Cloud Data Mining:** All personal transaction data is stored locally on the device in an encrypted Room SQLite database (`SQLCipher`).
* **Hardware-Bound Keys:** Master cryptographic keys never leave the device Secure Element (Android KeyStore).
* **Network Transparency:** Cloud synchronization requires explicit user configuration; the application functions 100% offline out-of-the-box.
* **No Leaked Secrets:** Environment files are strictly excluded from source control via `.gitignore`, with only sanitized `.env.example` templates committed.

---

## 🔮 8. Limitations & Future Roadmap

* **Planned Enhancements:**
  * Automated SMS transaction auto-categorization via Android SMS Retriever API.
  * Direct Bank Feed integration via open banking APIs (Account Aggregator framework).
  * PDF and CSV export for monthly tax and compliance reporting.
  * Multi-currency conversion with offline daily exchange rate caching.

---

## 📄 9. License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
