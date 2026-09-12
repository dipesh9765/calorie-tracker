# CalorieTracker: Comprehensive Bug, Integrity & Feature Gap Report

This document records the architectural, business rule, data integrity, and usability issues discovered during comprehensive testing and codebase audit.

---

## 1. Critical Bugs & Data Integrity Issues

### 🟢 Bug 1.1: Silent Duplicate-Food Overwrite (Data Loss) [FIXED]
- **Location**: [`MainActivity.kt:339-351`](file:///d:/DEVELOPMENT/android/calorie%20tracker/app/src/main/java/com/calorietracker/app/MainActivity.kt#L339-L351)
- **Severity**: **CRITICAL (Resolved)**
- **Status**: **FIXED** - `MainActivity.kt` now checks `parseResult.action`. If action is not `"UPDATE"` (i.e. `"CREATE"` by default), it always generates a new UUID and inserts a new meal without overwriting existing meals. Overwriting only occurs if the user explicitly commanded an `UPDATE`.
- **Root Cause**:
  ```kotlin
  val existing = allMealEntities.find {
      it.dateIso == entry.dateIso && it.foodName.equals(
          entry.foodName,
          ignoreCase = true
      )
  }
  val entityToSave = if (existing != null) {
      entry.copy(id = existing.id).toMealEntity()
  } else {
      entry.toMealEntity()
  }
  mealDao.insertMeal(entityToSave)
  ```
- **Observed Behavior**:
  If a user eats **"2 Rotis"** for lunch at 1:00 PM, and logs **"2 Rotis"** again for dinner at 8:00 PM on the same day, `existing` matches the lunch entry by name and date. The app copies the lunch entity's UUID and **silently overwrites and deletes the lunch entry in SQLite**, rather than logging a second meal! Similarly, taking 1 scoop of whey protein in the morning and 1 scoop in the evening causes the morning scoop to be lost.
- **Recommended Remediation**:
  Only update an existing meal if the AI explicitly detected an `action == "UPDATE"` targeting that meal, or if the user explicitly clicked an "Edit Meal" button. All standard meal logging should generate a new UUID and execute as a fresh `CREATE`.

---

### 🟢 Bug 1.2: AI "Action" (`CREATE` vs `UPDATE`) Is Discarded [FIXED]
- **Location**: [`AiNutritionRepositoryImpl.kt:114-133`](file:///d:/DEVELOPMENT/android/calorie%20tracker/app/src/main/java/com/calorietracker/app/data/repository/AiNutritionRepositoryImpl.kt#L114-L133)
- **Severity**: **HIGH (Resolved)**
- **Status**: **FIXED** - `IAiNutritionRepository.parseMealText` returns `AiMealParseResult(meal, advice, action)` and `AiRepository` returns `MealParseResult(mealEntry, coachAdvice, action)`, cleanly capturing and propagating the parsed AI action.
- **Root Cause**:
  The system prompt specifically instructs the AI model to determine:
  ```json
  "action": "CREATE" // or "UPDATE" if correcting a meal
  ```
  However, in `AiNutritionRepositoryImpl.kt`, `jsonObject.get("action")` is never parsed or propagated into the return object. It only returns `Pair<Meal, String>`. As a result, the calling layer has no idea whether the user asked to edit an existing meal or log a new one.
- **Recommended Remediation**:
  Include `action: String` in a dedicated `MealParseResult` model returned by `IAiNutritionRepository`.

---

### 🟠 Bug 1.3: `weeklySummaries` Cache Invalidation Failure (`remember(allMeals.size)`)
- **Location**: [`MainActivity.kt:194`](file:///d:/DEVELOPMENT/android/calorie%20tracker/app/src/main/java/com/calorietracker/app/MainActivity.kt#L194)
- **Severity**: **MEDIUM**
- **Root Cause**:
  ```kotlin
  val weeklySummaries = remember(allMeals.size) {
      val grouped = allMeals.groupBy { it.dateIso }
      // ...
  }
  ```
  The computation is keyed solely on `allMeals.size`.
- **Observed Behavior**:
  If any meal's calories, protein, or name is modified (or if a meal is updated without changing the total count of meals), or if the user changes their target calorie deficit in Profile, `weeklySummaries` does not recompute. The Analytics screen continues to display outdated daily totals.
- **Recommended Remediation**:
  Key the memoization on `allMeals` and `userProfile.targetDailyCalories`:
  `remember(allMeals, userProfile.targetDailyCalories)`.

---

## 2. Usability & UI Gaps

### 🟡 Bug 2.1: Missing Profile Input Fields for Weight, Target Weight, and Height [DEFERRED]
- **Location**: [`ProfileScreen.kt:40-43`](file:///d:/DEVELOPMENT/android/calorie%20tracker/app/src/main/java/com/calorietracker/app/ui/screens/ProfileScreen.kt#L40-L43) & [`ProfileScreen.kt:233-235`](file:///d:/DEVELOPMENT/android/calorie%20tracker/app/src/main/java/com/calorietracker/app/ui/screens/ProfileScreen.kt#L233-L235)
- **Severity**: **HIGH**
- **Status**: **DEFERRED BY USER** (Current profile metrics are assumed static for Dipesh; to be added in future release).
- **Description**:
  In `ProfileScreen.kt`, state variables exist:
  ```kotlin
  var currentWeight by remember { mutableStateOf(userProfile.weightKg.toString()) }
  var targetWeight by remember { mutableStateOf(userProfile.targetWeightKg.toString()) }
  var userHeight by remember { mutableStateOf(userProfile.heightCm.toString()) }
  ```
  And when "Save Configuration" is clicked, these state values are copied into `userProfile`.
  **However, there are NO text fields or inputs rendered in the UI for Weight, Target Weight, or Height.**
  Users can only configure AI provider API keys and model names, with no way to modify their physical body metrics.
- **Recommended Remediation**:
  Add `OutlinedTextField` composables for Current Weight (kg), Target Goal Weight (kg), and Height (cm) in `ProfileScreen`.

---

### 🟡 Bug 2.2: Nutrition Metrics Are Not Recalculated on Profile Save
- **Location**: [`ProfileScreen.kt:222-238`](file:///d:/DEVELOPMENT/android/calorie%20tracker/app/src/main/java/com/calorietracker/app/ui/screens/ProfileScreen.kt#L222-L238)
- **Severity**: **MEDIUM**
- **Description**:
  Even if body weight or height is updated, [`NutritionCalculator`](file:///d:/DEVELOPMENT/android/calorie%20tracker/app/src/main/java/com/calorietracker/app/domain/usecase/NutritionCalculator.kt) is never invoked. The user's `bmrCalories`, `tdeeMaintenanceCalories`, `targetDailyCalories`, and `targetDailyProteinGrams` remain permanently fixed at their initial hardcoded constants (2,105 kcal, 2,900 kcal, 2,300 kcal, 180g protein).
- **Recommended Remediation**:
  Upon profile save, call:
  ```kotlin
  val bmr = calculator.calculateBmr(updatedWeight, updatedHeight, profile.age, profile.gender)
  val tdee = calculator.calculateTdee(bmr, profile.gymDaysPerWeek)
  val targetCal = calculator.calculateTargetCalories(tdee)
  val targetProtein = calculator.calculateProteinTarget(updatedWeight)
  ```
  and save the calculated targets.

---

### 🟡 Bug 2.3: Hardcoded 2,300 kcal and 180g Protein Across Screens
- **Locations**:
  1. [`AnalyticsScreen.kt:258-263`](file:///d:/DEVELOPMENT/android/calorie%20tracker/app/src/main/java/com/calorietracker/app/ui/screens/AnalyticsScreen.kt#L258-L263):
     - `"${summary.totalCalories} / 2,300 kcal"` (hardcoded string)
     - `if (summary.totalCalories <= 2300) "In Deficit" else "Over Budget"` (hardcoded comparison, ignoring `summary.targetCalories`)
  2. [`AnalyticsScreen.kt:97`](file:///d:/DEVELOPMENT/android/calorie%20tracker/app/src/main/java/com/calorietracker/app/ui/screens/AnalyticsScreen.kt#L97): Title text: `"Daily Calories vs Target (2,300 kcal)"`.
  3. [`LogHistoryScreen.kt:117-119`](file:///d:/DEVELOPMENT/android/calorie%20tracker/app/src/main/java/com/calorietracker/app/ui/screens/LogHistoryScreen.kt#L117-L119):
     - `targetCalories = 2300`, `targetProtein = 180f` (hardcoded in `DailySummary` construction on date click).
  4. [`ProfileScreen.kt:76, 101`](file:///d:/DEVELOPMENT/android/calorie%20tracker/app/src/main/java/com/calorietracker/app/ui/screens/ProfileScreen.kt#L76):
     - `"Personal Baseline (25 yrs, Male, 188cm)"` (hardcoded string).
     - `"Weight Loss Goal: 105 kg → 90 kg (600 kcal deficit + 180g protein)"` (hardcoded string).
  5. [`HomeScreen.kt:87`](file:///d:/DEVELOPMENT/android/calorie%20tracker/app/src/main/java/com/calorietracker/app/ui/screens/HomeScreen.kt#L87):
     - `"Goal: ... (2,300 kcal)"` (hardcoded string).
- **Recommended Remediation**:
  Dynamically format these strings and thresholds using `userProfile.targetDailyCalories`, `userProfile.targetDailyProteinGrams`, `userProfile.age`, and `userProfile.heightCm`.

---

## 3. Missing Features

### ⚪ Feature 3.1: Persistent Creatine Tracking
- **Locations**: [`MainActivity.kt:189`](file:///d:/DEVELOPMENT/android/calorie%20tracker/app/src/main/java/com/calorietracker/app/MainActivity.kt#L189), [`GetDailyNutritionSummaryUseCase.kt:24`](file:///d:/DEVELOPMENT/android/calorie%20tracker/app/src/main/java/com/calorietracker/app/domain/usecase/GetDailyNutritionSummaryUseCase.kt#L24)
- **Description**:
  The creatine toggle state `creatineTaken` is held purely in memory (`var creatineTaken by remember { mutableStateOf(true) }`). It is never written to Room SQLite or SharedPreferences. If the user exits or kills the app, the state resets. In `GetDailyNutritionSummaryUseCase`, `creatineTaken = true` is hardcoded for all historical dates.
- **Recommended Implementation**:
  Persist daily creatine completion in SharedPreferences (`"creatine_taken_$dateIso"`) or a dedicated Room table.

---

### ⚪ Feature 3.2: Water Intake Tracking
- **Locations**: [`UserProfile.kt:28`](file:///d:/DEVELOPMENT/android/calorie%20tracker/app/src/main/java/com/calorietracker/app/data/model/UserProfile.kt#L28) (`targetWaterLiters = 4.0f`), [`DailySummary.kt:12`](file:///d:/DEVELOPMENT/android/calorie%20tracker/app/src/main/java/com/calorietracker/app/data/model/DailySummary.kt#L12) (`waterLiters = 0f`)
- **Description**:
  The data models declare support for water tracking with a daily target of 4.0 liters. However, there is zero UI (no water logging cards, quick-add +250ml / +500ml buttons, or progress bars) and no database persistence for water logs.
- **Recommended Implementation**:
  Add a Water Intake card to `HomeScreen` with quick increment buttons and persist daily water total.

---

### ⚪ Feature 3.3: Clean Architecture Wiring (`HomeViewModel` Unused)
- **Locations**: [`MainActivity.kt`](file:///d:/DEVELOPMENT/android/calorie%20tracker/app/src/main/java/com/calorietracker/app/MainActivity.kt) vs [`HomeViewModel.kt`](file:///d:/DEVELOPMENT/android/calorie%20tracker/app/src/main/java/com/calorietracker/app/presentation/home/HomeViewModel.kt)
- **Description**:
  A Clean Architecture ViewModel [`HomeViewModel`](file:///d:/DEVELOPMENT/android/calorie%20tracker/app/src/main/java/com/calorietracker/app/presentation/home/HomeViewModel.kt) was created along with domain use cases ([`LogMealWithAiUseCase`](file:///d:/DEVELOPMENT/android/calorie%20tracker/app/src/main/java/com/calorietracker/app/domain/usecase/LogMealWithAiUseCase.kt)) and interfaces ([`IUserProfileRepository`](file:///d:/DEVELOPMENT/android/calorie%20tracker/app/src/main/java/com/calorietracker/app/domain/repository/IUserProfileRepository.kt)).
  However:
  1. `MainActivity.kt` bypasses `HomeViewModel` entirely and manages all state and logic inline.
  2. `IUserProfileRepository` has no production implementation (`PreferencesRepository` does not implement `IUserProfileRepository`).
- **Recommended Implementation**:
  Have `PreferencesRepository` implement `IUserProfileRepository`, instantiate `HomeViewModel` via ViewModelProvider/Hilt, and bind `HomeScreen` to `HomeViewModel.uiState`.
