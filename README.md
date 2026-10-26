# 🥗 Calorie Tracker AI - Multi-LLM Smart Nutrition & Macro Tracker

An enterprise-grade Android application built with **Kotlin**, **Jetpack Compose**, **Clean Architecture**, and **Multi-LLM AI Integration** (Google Gemini 3.6 Flash, OpenAI GPT-4o-mini, Anthropic Claude 3.5 Sonnet, and DeepSeek).

---

## 📥 Download APK

| Package | Direct Download Link | Description |
| :--- | :--- | :--- |
| **Release APK** | [**Download app-release.apk**](https://github.com/dipesh9765/calorie-tracker/releases/latest/download/app-release.apk) | Production signed APK (~10.9 MB) |
| **Debug APK** | [**Download app-debug.apk**](https://github.com/dipesh9765/calorie-tracker/releases/latest/download/app-debug.apk) | Development debug build (~16.6 MB) |

> 📌 **Note**: You can also download the latest APK directly from the [**GitHub Releases Page**](https://github.com/dipesh9765/calorie-tracker/releases).

---

## ✨ Features

- 🤖 **Multi-LLM AI Engine**:
  - **Google Gemini** (`gemini-3.6-flash` / `gemini-3.5-flash`) with native `responseSchema` JSON enforcement.
  - **OpenAI** (`gpt-4o-mini`) with `json_schema` strict mode.
  - **Anthropic Claude** (`claude-3-5-sonnet-20240620`).
  - **DeepSeek** (`deepseek-chat`) with `json_object` mode.
- 🧑‍⚕️ **AI Dietitian Chat**:
  - A conversation, not a one-shot parser: if a portion, oil/ghee, brand or hidden ingredient is unclear, the dietitian asks short follow-up questions and keeps refining until it can give a confident estimate, which you then save to your log.
  - **Photos**: take a picture with the camera or pick one from the gallery for a calorie estimate (Gemini, OpenAI and Claude; DeepSeek is text-only).
  - **Shortcuts**: one-tap "How am I doing today?", "What should I eat next?", "Review my week" and "I weighed in today". Tell it your weight in chat and it logs it and refreshes your targets.
  - **Memory**: it remembers your supplements (e.g. exact protein powder and scoop size), habits, allergies and preferences, and uses them in chat, quick-log and notifications. Say "I switched to a 40 g scoop" and it updates the memory. View, add, edit or delete memories from the 🧠 button in the chat header.
- 🎯 **Your profile, your targets**: enter age, height, weight, goal (lose / maintain / gain), pace, activity level, cuisine and diet type. Daily calories, protein and water are generated (Mifflin-St Jeor with a safe-pace cap and calorie floor) and recalculated whenever you update your details, or set them by hand. Weight changes are kept as a history and fed to the dietitian as a trend.
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
