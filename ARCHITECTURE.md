# Smart Budget Tracker — Architecture & Engineering Specification

> **Version:** 2.0.0  
> **Status:** Production-Grade FinTech Architecture  
> **Target Platform:** Android (Jetpack Compose, Kotlin Multiplatform-ready Domain)  
> **Compliance & Standards:** IEEE 754 Guarded, AES-256-GCM Tink AEAD, SQLCipher, Clean Architecture

---

## Table of Contents
1. [Executive Architectural Overview](#1-executive-architectural-overview)
2. [Clean Architecture Layering & Module Topology](#2-clean-architecture-layering--module-topology)
3. [Financial Precision Protocol (`Money` Engine)](#3-financial-precision-protocol-money-engine)
4. [Offline Sync & Distributed Consistency Engine](#4-offline-sync--distributed-consistency-engine)
5. [Behavioral AI & Personalization Feedback Loop](#5-behavioral-ai--personalization-feedback-loop)
6. [Security, Cryptography & Fraud Prevention](#6-security-cryptography--fraud-prevention)
7. [State Management & Typed Error Taxonomy](#7-state-management--typed-error-taxonomy)
8. [Database Schema, Indexing & Query Optimizations](#8-database-schema-indexing--query-optimizations)
9. [Verification, Testing Strategy & Benchmarks](#9-verification-testing-strategy--benchmarks)

---

## 1. Executive Architectural Overview

The **Smart Budget Tracker** is engineered as a zero-compromise, offline-first personal finance platform. Unlike standard budgeting utilities that suffer from floating-point accumulation drift, network unreliability, and naive notification spam, this system implements:

- **Mathematical Correctness**: Complete elimination of IEEE 754 floating-point errors via an unboxed `@JvmInline value class Money(val paise: Long)` handling all financial sums down to sub-unit currency integers.
- **Deterministic Distributed Consistency**: A robust Last-Write-Wins (LWW) conflict resolver reinforced by monotonic `syncVersion` tie-breaking, soft-delete tombstones, and clock-skew tolerance.
- **Behavioral Finance AI Engine**: A closed-loop cognitive coaching system featuring Weighted Moving Average (WMA) spend forecasting, Z-Score anomaly detection, habit streak preservation, and multi-factor ranking.
- **Enterprise-Grade Cryptographic Defense**: Modern migration from deprecated `EncryptedSharedPreferences` to Google Tink AEAD authenticated encryption, Android Keystore, SQLCipher database encryption, and heuristic fraud detection.

```
┌────────────────────────────────────────────────────────────────────────┐
│                          PRESENTATION LAYER                            │
│    Jetpack Compose UI  •  MVI/MVVM Flow  •  Material 3 Expressive      │
│  [Dashboard]   [Expenses]   [Goals]   [Analytics]   [AI Financial Coach]│
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Observes Resource<T> & Pure Models
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                             DOMAIN LAYER                               │
│              Pure Kotlin (No Android Dependencies, 100% Testable)       │
│                                                                        │
│  ┌───────────────────────┐  ┌────────────────────────────────────────┐ │
│  │ Feature Use Cases     │  │ Behavioral AI Engine                   │ │
│  │ • AddExpenseUseCase   │  │ • AICoachEngine      • StreakEngine    │ │
│  │ • GetDashboardData    │  │ • SpendingPredictor  • ToneEngine      │ │
│  │ • SyncDataUseCase     │  │ • AnomalyDetector    • Ranker          │ │
│  │ • DepositToGoal       │  │ • ConversationMemory • NotificationDedup│ │
│  └───────────┬───────────┘  └───────────────────┬────────────────────┘ │
│              │                                  │                      │
│              └─────────────────┬────────────────┘                      │
│                                ▼                                       │
│                     [IFinanceRepository Contracts]                     │
│                     [Money Value Class (Paise)]                        │
│                     [SyncableEntity Interface]                         │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                              DATA LAYER                                │
│  ┌───────────────────────────────────┐ ┌─────────────────────────────┐ │
│  │ Local Persistence (Room / SQLite) │ │ Remote Sync (Firebase/REST) │ │
│  │ • SQLCipher 256-bit AES           │ │ • Firestore / REST API      │ │
│  │ • Composite Index: (user, date)   │ │ • SyncWorker WorkManager    │ │
│  │ • SQL Grouped Aggregation         │ │ • Conflict Resolution Hub   │ │
│  │ • Amount stored in integer paise  │ │ • Tombstone Soft-Deletes    │ │
│  └─────────────────┬─────────────────┘ └──────────────┬──────────────┘ │
│                    └─────────────────┬────────────────┘                │
│                                      ▼                                 │
│                         FinanceRepository Implementation               │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                              CORE LAYER                                │
│  • Google Tink AEAD + KeyStore       • PerformanceTracer & Telemetry   │
│  • FraudDetectionEngine              • Monotonic SyncConflictResolver  │
│  • AppError Typed Taxonomy           • Resource<T> Reactive Envelope   │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Clean Architecture Layering & Module Topology

The project enforces strict directional dependencies: `Presentation -> Domain <- Data` with `Core` cross-cutting utilities.

### 2.1 Domain Layer (`com.example.domain`)
- **Zero Platform Dependencies**: Contains no `android.*` imports. Fully portable to Kotlin Multiplatform (KMP), JVM, or iOS targets.
- **Pure Use Cases**:
  - `AddExpenseUseCase`: Orchestrates input validation, tag normalization, integer paise calculation, fraud scoring, persistence, and milestone evaluation.
  - `GetDashboardDataUseCase`: Composes concurrent streams for profile, monthly spend, category aggregations, recent expenses, and goals.
  - `GenerateInsightsUseCase`: Triggers behavioral analysis and runs multi-factor ranking.
  - `SyncDataUseCase`: Coordinates bidirectional cloud sync with deterministic conflict resolution.
- **Domain Models**:
  - `Money`: Value class representing currency in integer paise.
  - `PureExpense`, `PureGoal`, `PureProfile`: Immutable domain records implementing `SyncableEntity`.
  - `Insight`, `UserBehaviorProfile`, `CoachTone`: Behavioral intelligence models.

### 2.2 Presentation Layer (`com.example.ui`)
- **Declarative UI**: Built with Jetpack Compose, Material 3, Dynamic Color theming, and dark/light mode parity.
- **Unidirectional Data Flow (UDF)**: ViewModels expose immutable `StateFlow<Resource<ViewState>>`. UI events are dispatched as intent actions.
- **Decoupled Business Logic**: ViewModels do not perform math, validation, or formatting; they delegate directly to domain use cases.

### 2.3 Data Layer (`com.example.data`)
- **Room Database (`AppDatabase`)**: Local single source of truth backed by SQLCipher encryption.
- **DAO Optimization**: Direct SQL-side aggregations (`totalsBetweenGrouped`) to bypass entity deserialization overhead on hot analytics screens.
- **Repository Implementation (`FinanceRepository`)**: Mediates between Room local cache and Firebase Firestore remote cloud, emitting reactive Kotlin `Flow<Resource<T>>`.

### 2.4 Core Layer (`com.example.core`)
- **Security**: Google Tink AEAD cryptographic providers, biometric gating, and fraud detection heuristics.
- **Offline Sync**: Monotonic timestamp + version tie-breaker resolving distributed conflict states.
- **Error Taxonomy**: Exhaustive sealed hierarchy of domain, auth, database, network, and validation errors.

---

## 3. Financial Precision Protocol (`Money` Engine)

### 3.1 The IEEE 754 Floating-Point Vulnerability
Standard floating-point numbers (`Double`, `Float`) represent fractional values as binary approximations:
```kotlin
0.1 + 0.2 = 0.30000000000000004 // Drift occurs in IEEE 754
```
In high-frequency fintech systems (daily budgeting, compounding interest, cumulative category totals), these micro-deviations compound into irreconcilable account imbalances, ledger discrepancies, and broken audit logs.

### 3.2 The `@JvmInline value class Money` Solution
The domain architecture introduces an unboxed value class:

```kotlin
@JvmInline
value class Money(val paise: Long) : Comparable<Money> {
    operator fun plus(other: Money): Money = Money(this.paise + other.paise)
    operator fun minus(other: Money): Money = Money(this.paise - other.paise)
    operator fun times(factor: Double): Money = Money(Math.round(this.paise * factor))
    operator fun div(divisor: Long): Money = Money(this.paise / divisor)
    
    fun rupees(): Double = paise / 100.0
    fun format(): String = String.format(Locale.ENGLISH, "₹%,.2f", rupees())

    companion object {
        val ZERO = Money(0L)
        fun fromRupees(amount: Double): Money = Money(Math.round(amount * 100.0))
        fun fromPaise(paise: Long): Money = Money(paise)
    }
}
```

#### Key Engineering Guarantees:
1. **Zero Heap Allocations**: Under JVM compilation, `@JvmInline` compiles down to a primitive `long` in bytecode. Hot aggregation loops across tens of thousands of transactions allocate zero heap objects, preventing garbage collector pauses.
2. **Sub-Unit Integer Storage**: Room database stores `amountPaise: Long`, completely eliminating floating-point math inside SQLite queries.
3. **Backward Compatibility**: Existing UI components and legacy models retain `amount: Double` via calculated getters while all domain logic runs on `money.paise`.

---

## 4. Offline Sync & Distributed Consistency Engine

### 4.1 Distributed Conflict Anatomy
In multi-device, offline-first personal finance:
1. Device A modifies an expense offline at $T_1$.
2. Device B modifies the same expense offline at $T_2$.
3. Both devices synchronize with Firestore/Cloud when reconnected.

Naive Last-Write-Wins (LWW) based solely on client wall-clock timestamps fails when:
- Device clocks suffer from drift or malicious tampering.
- Two events occur within the same millisecond.
- A delete operation (tombstone) is overwritten by a delayed stale update.

### 4.2 The `SyncableEntity` Contract & Monotonic Versioning
Every synchronized entity implements:

```kotlin
interface SyncableEntity {
    val id: String
    val updatedAt: Long
    val isDeleted: Boolean
    val syncVersion: Long
}
```

```
       Incoming Remote Entity (R)  vs  Local Entity (L)
                      │
                      ▼
             Is R or L Deleted?
              ├── R.isDeleted && L.isDeleted ──► Tombstone Wins (Keep Deleted)
              ├── R.isDeleted && !L.isDeleted ─► R Wins if R.updatedAt >= L.updatedAt - 50ms
              └── !R.isDeleted && L.isDeleted ─► L Wins (Do not resurrect deleted item)
                      │ (Neither is deleted)
                      ▼
         Abs(R.updatedAt - L.updatedAt) > 50ms?
              ├── Yes: Higher updatedAt Wins (Standard LWW)
              └── No (Clock Skew / Near-Tie):
                      ▼
             Compare syncVersion:
              ├── R.syncVersion > L.syncVersion ──► Remote Wins
              ├── L.syncVersion > R.syncVersion ──► Local Wins
              └── R.syncVersion == L.syncVersion ─► Lexicographical Id Tie-Breaker
```

### 4.3 Soft-Delete Tombstones
Entities are never removed with hard SQL `DELETE` during offline mode. Instead:
- `isDeleted = true` and `updatedAt = System.currentTimeMillis()`.
- Room queries filter out records with `WHERE isDeleted = 0`.
- SyncWorker transmits tombstones to cloud endpoints to delete remote replicas.
- Local vacuum sweeps purges tombstones older than 90 days after cloud ACK.

---

## 5. Behavioral AI & Personalization Feedback Loop

The Smart Budget Tracker incorporates a behavioral economics engine designed to nudge users toward positive financial habits without overwhelming them.

### 5.1 Cognitive Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────┐
│                        RAW FINANCIAL EVENTS                            │
│           Daily Expenses • Category Spends • Goals • Logins            │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     BEHAVIORAL DETECTOR PIPELINE                       │
│                                                                        │
│   ┌─────────────────────┐  ┌─────────────────────┐  ┌────────────────┐ │
│   │ SpendingPredictor   │  │ AnomalyDetector     │  │ StreakEngine   │ │
│   │ • 4-Week WMA        │  │ • Z-Score (z > 2.2) │  │ • Break Risk   │ │
│   │ • Weekend/Weekday   │  │ • Min 5 Baseline    │  │ • Tolerance    │ │
│   └──────────┬──────────┘  └──────────┬──────────┘  └────────┬───────┘ │
│              │                        │                      │         │
│              └─────────────────┬──────┴──────────────────────┘         │
│                                ▼                                       │
│                    Raw Insight Candidates Pool                         │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                  MULTI-FACTOR INSIGHT RANKER                           │
│   Score = 0.35*Urgency + 0.30*Impact + 0.20*Relevance + 0.15*Action    │
│   • Exponential Recency Decay: e^(-λ * ageHours)                       │
│   • Currency Saturation: min(1.0, impactAmount / ₹5,000)               │
│   • Dynamic Relevance: UserBehaviorProfile click-rate weights          │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Top 3 Highest Ranked Insights
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                 EMOTIONAL AI & TIMING DISPATCHER                       │
│   • ToneEngine: EMPATHETIC / DIRECT / CELEBRATORY (preserving numbers) │
│   • NotificationDeduper: Epoch-day deduplication suppression           │
│   • Quiet Hours Filter: Queued for Morning/Evening Rituals             │
└────────────────────────────────────────────────────────────────────────┘
```

### 5.2 Key Algorithms:
1. **Weighted Moving Average (WMA) Predictor**:
   $$\text{WMA} = \frac{\sum_{i=1}^{N} w_i \cdot x_i}{\sum_{i=1}^{N} w_i}, \quad w = [0.10, 0.20, 0.30, 0.40]$$
   Evaluates weekend and weekday patterns independently to forecast month-end budget overshoots.
2. **Degenerate-Guarded Z-Score Anomaly Detector**:
   $$z = \frac{x - \mu}{\sigma + \epsilon}$$
   Safeguarded with $\sigma < 1.0$ fallback to percentage variance, and sample size threshold ($N \ge 5$) preventing false-positive anomaly alarms on new accounts.
3. **Notification Deduplication Protocol**:
   Stores `sent_epoch_day` per insight category. Suppresses repetitive alerts within the same calendar day while guaranteeing critical threshold bypass.

---

## 6. Security, Cryptography & Fraud Prevention

### 6.1 Cryptographic Infrastructure
- **Hardware-Backed Android KeyStore**: Generates and seals the 256-bit AES Master Key inside the device Secure Element (SE) / StrongBox.
- **Google Tink AEAD (`AeadConfig`)**: Replaces deprecated `EncryptedSharedPreferences`. Provides authenticated symmetric encryption with associated data (AEAD) using AES-GCM-256 for authentication tokens, API keys, and biometric session salts.
- **SQLCipher SQLite Encryption**: 256-bit AES encryption of all Room database pages on disk. Passphrases are generated dynamically via KeyStore-backed secrets.

### 6.2 Fraud Detection Engine
Evaluates every transaction before commitment:
- **Velocity Check**: Detects rapid bursts (>3 transactions within 60 seconds).
- **High-Value Outlier**: Flags single transactions exceeding 300% of user 30-day average.
- **Nocturnal Anomaly**: Warns on high-value spending between 01:00 AM and 05:00 AM.
- **Duplicate Detection**: Identifies identical merchant + amount transactions occurring within a 3-minute window.

---

## 7. State Management & Typed Error Taxonomy

### 7.1 Reactive Resource State (`Resource<T>`)
```kotlin
sealed class Resource<out T> {
    data class Success<out T>(val data: T, val isStale: Boolean = false) : Resource<T>()
    data class Error(val error: AppError, val cachedData: Any? = null) : Resource<Nothing>()
    data class Loading(val cachedData: Any? = null) : Resource<Nothing>()
}
```
Supports the **Cached-Then-Refresh** pattern: emits cached local database records immediately with `isStale = true` or `Loading(cachedData)`, then refreshes asynchronously from the network.

### 7.2 Domain Error Hierarchy (`AppError`)
```kotlin
sealed class AppError : Exception() {
    sealed class Network : AppError() { object NoConnectivity : Network(); object Timeout : Network() }
    sealed class Auth : AppError() { object SessionExpired : Auth(); object BiometricRejected : Auth() }
    sealed class Database : AppError() { data class DiskFull(val bytesNeeded: Long) : Database() }
    sealed class Validation : AppError() { data class InvalidAmount(val reason: String) : Validation() }
    sealed class Conflict : AppError() { data class VersionMismatch(val id: String) : Conflict() }
}
```

---

## 8. Database Schema, Indexing & Query Optimizations

### 8.1 Composite Indexes
To ensure sub-10ms response times on datasets exceeding 50,000 transactions, the Room database defines targeted composite indexes:

```kotlin
@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["userEmail", "date"], name = "idx_expenses_user_timestamp"),
        Index(value = ["userEmail", "category"], name = "idx_expenses_user_category")
    ]
)
```

### 8.2 SQL-Side Grouped Aggregations
Rather than loading thousands of entity records into memory and executing Kotlin `groupBy`:
```kotlin
@Query("""
    SELECT category, SUM(amountPaise) as totalPaise FROM expenses
    WHERE userEmail = :userEmail AND isDeleted = 0 AND date BETWEEN :start AND :end
    GROUP BY category
""")
suspend fun totalsBetweenGrouped(userEmail: String, start: Long, end: Long): List<CategoryTotalRow>
```
Executed natively by SQLite with index-scan optimization.

---

## 9. Verification, Testing Strategy & Benchmarks

### 9.1 Test Matrix
The codebase maintains comprehensive test coverage across 7 dedicated test suites:

| Suite | Component Tested | Assertions / Scenarios |
|---|---|---|
| `FintechLaunchChecklistTest` | `Money` paise math, `NotificationDeduper`, LWW `syncVersion` | 7 tests: floating-point precision, day dedup, conflict resolution |
| `BehaviorDrivenAssistantTest` | AI Coach, Personalization, Dynamic Ranking | 10 tests: WMA, Z-Score, Streak break risk, tone rewrites |
| `BehavioralNotificationSystemTest` | Alert Dispatcher, Quiet Hours, Anomaly Detection | 11 tests: quiet hours, digest batching, degenerate variance |
| `AddExpenseUseCaseTest` | Validation, Fraud Gate, Tag Normalization | 9 tests: invalid amount, empty category, fraud detection triggers |
| `SyncConflictResolverTest` | Distributed Sync, Clock Skew, Tombstones | 8 tests: timestamp comparison, version tie-breaker, deleted items |
| `FintechSecurityManagerTest` | AES-256-GCM Encryption, Key Store Decryption | 6 tests: tamper detection, ciphertext uniqueness, null protection |
| `VoiceExpenseParserTest` | NLP parsing of voice expense inputs | 18 tests: multi-lingual parsing, currency detection, entity extraction |

### 9.2 Build & Execution Command
```bash
# Run complete unit test suite
./gradlew testDebugUnitTest --no-daemon
```

---
*Smart Budget Tracker Architecture Specification — Approved for Production Deployment.*
