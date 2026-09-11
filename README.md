# 🥗 Calorie Tracker AI - Multi-LLM Smart Nutrition & Macro Tracker

An enterprise-grade Android application built with **Kotlin**, **Jetpack Compose**, **Clean Architecture**, and **Multi-LLM AI Integration** (Google Gemini 3.6 Flash, OpenAI GPT-4o-mini, Anthropic Claude 3.5 Sonnet, and DeepSeek).

---

## ✨ Features

- 🤖 **Multi-LLM AI Engine**:
  - **Google Gemini** (`gemini-3.6-flash` / `gemini-3.5-flash`) with native `responseSchema` JSON enforcement.
  - **OpenAI** (`gpt-4o-mini`) with `json_schema` strict mode.
  - **Anthropic Claude** (`claude-3-5-sonnet-20240620`).
  - **DeepSeek** (`deepseek-chat`) with `json_object` mode.
- 🕒 **Past-Day & Relative Date Logging**:
  - Automatically parses natural language expressions like *"yesterday night I ate 2 rotis with egg curry"* or *"on Sept 8 I had saoji chicken"*.
  - Automatically resolves relative date references to past ISO dates (`YYYY-MM-DD`).
  - Supports both **CREATE** and **UPDATE** actions for past days.
- 🏷️ **Brand & Packaged Product Nutrition Estimation**:
  - Accurately references official package nutrition labels and scales macros pro-rata for commercial products (e.g. *Nakpro Perform Whey*, *Amul High Protein Lassi*, *Haldiram's Poha*, *Subway*, *Epigamia Greek Yogurt*).
- 📊 **Interactive Day Details Modal**:
  - Tap any past date or summary card on the Analytics or History screens to view itemized food breakdowns, macro splits (Protein, Carbs, Fat), and total calorie deficit/surplus indicators.
- 🎨 **Modern Design & 3D Launcher Icon**:
  - Sleek dark theme with liquid bubble progress indicators, custom macro breakdown bars, dynamic glassmorphic cards, and a custom 3D app icon.
- 🛡️ **Network & Parsing Resilience**:
  - 60-second connection & read timeouts eliminating `SocketTimeoutException`.
  - Type-safe primitive parsing protecting against floating-point/string conversion issues.

---

## 🏗️ Architecture

The app follows **Android Clean Architecture** principles with distinct layers:

```
app/src/main/java/com/calorietracker/app/
├── data/
│   ├── model/         # Data entities (UserProfile, MealEntry, DailySummary)
│   ├── remote/        # HTTP API Adapters (Gemini, OpenAI, Claude, DeepSeek)
│   └── repository/    # IAiNutritionRepository & IMealRepository implementations
├── domain/
│   ├── model/         # Domain models (Meal, DailyNutritionSummary, AiProviderType)
│   ├── repository/    # Domain repository interfaces
│   └── usecase/       # Business logic (LogMealWithAiUseCase, GetDailyNutritionSummaryUseCase)
├── presentation/
│   └── home/          # HomeViewModel & Unidirectional Data Flow (UDF) UiState
└── ui/
    ├── components/    # Reusable Compose components (DayDetailsDialog, MacroBar, LiquidBubble)
    ├── screens/       # HomeScreen, AnalyticsScreen, LogHistoryScreen, ProfileScreen
    └── theme/         # App Colors, Typography, and Material 3 Theme
```

---

## 🧪 Testing Kit

The project includes an automated test kit under `app/src/test/java/com/calorietracker/app/`:

1. **`AiAdapterTest.kt`**: Validates JSON string extraction, markdown wrapper stripping, and mock adapter responses.
2. **`StructuredOutputTest.kt`**: Validates native API JSON schemas (Gemini `responseSchema`, OpenAI `json_schema`), string primitive conversion, and numeric type coercion.
3. **`NetworkTimeoutTest.kt`**: Tests 60s network timeout error mapping and offline network recovery guidance.

### Run Automated Tests
```bash
./gradlew test
```

---

## 🚀 Building & Running

### Prerequisites
- Android Studio Ladybug / Jellyfish or Gradle 8.13+
- JDK 17 or JDK 21
- Android SDK 35 (Target SDK 35, Min SDK 26)

### Build Debug APK
```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Build Release APK
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.
