# ADR 0004: One domain `Meal` model and Flow-based state

- Status: accepted

## Context
The code had three near-identical meal types (`MealEntry`, `Meal`, `MealEntity`), two daily-summary types, and a hand-rolled `profileVersion` counter used to tell the UI to reload the profile. The logic that decided whether an AI reply creates or replaces a meal lived inside `MainActivity`, untested.

## Decision
- `Meal` (domain) and `MealEntity` (Room) only; mapping functions live next to the entity.
- Room queries and the profile repository expose `Flow`/`StateFlow`; ViewModels combine them into a single immutable UI state.
- The create-versus-update rule is part of `LogMealWithAiUseCase`: a new meal is always a new row, and only an explicit AI `UPDATE` replaces the same-named meal on that day.
- Time is injected (`Clock`), and "today" is never computed with locale-dependent formatters in business code.

## Consequences
- Fewer mappers to keep in sync, and the earlier "same food logged twice overwrites the first" bug is covered by a test.
- Screens never refresh manually; changes anywhere propagate.
- The Today screen reads the date once per ViewModel, so a session left open across midnight keeps showing the old day until reopened (documented in the README).
