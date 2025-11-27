# Screen-by-Screen Parity Plan

## Project: Phoenix 2.0 KMP Migration

**Goal:** Achieve 100% UI and functionality parity with VitruvianProjectPhoenix-original
**Created:** 2025-11-26
**Status:** Planning Complete - Ready for Implementation

---

## Executive Summary

Based on analysis of both `PROJECT_COMPARISON.md` and `UI_COMPONENTS_MIGRATION_REPORT.md`:

| Category | Count | Status |
|----------|-------|--------|
| Fully Complete Screens | 6 | Ready for QA |
| Partially Complete Screens | 4 | Need enhancement |
| Stub/Placeholder Screens | 7 | Need full implementation |
| Missing Screens | 3 | Need to create |
| **Total Screens** | **20** | |

**Current Implementation:** ~45-50%
**Target:** 100%

---

## Phase 1: Critical Path - Core Workout Flow

These screens are essential for basic app functionality.

### 1.1 WorkoutTab.kt [CRITICAL - STUB]

**Current:** 2.7KB (3% of parent's 90KB)
**Parent Location:** `presentation/screen/WorkoutTab.kt`
**KMP Location:** `shared/.../presentation/screen/WorkoutTab.kt`

**Missing Features:**
- [ ] Connection status display
- [ ] Device selector
- [ ] Quick start workout button
- [ ] Recent workouts list
- [ ] Exercise quick-select cards
- [ ] Mode selector (Old School, Pump, TUT, Echo)
- [ ] Weight adjustment controls
- [ ] Real-time metrics display integration

**Dependencies:**
- MainViewModel (needs full implementation)
- BleRepository (needs platform implementations)
- ConnectionStatusBanner component
- Exercise selection components

**Estimated Effort:** HIGH (3-5 days)

---

### 1.2 JustLiftScreen.kt [PARTIAL]

**Current:** 8.5KB (24% of parent's 35KB)
**Status:** Basic workout flow exists but reduced UI complexity

**Missing Features:**
- [ ] Full weight picker with wheel interface
- [ ] Mode-specific configuration panels
- [ ] Echo mode level selector
- [ ] Eccentric load percentage selector
- [ ] Rep target configuration
- [ ] AMRAP toggle
- [ ] Exercise video preview
- [ ] Previous workout stats display

**Dependencies:**
- ExercisePickerDialog component
- CustomNumberPicker component
- Mode configuration models

**Estimated Effort:** MEDIUM (2-3 days)

---

### 1.3 ActiveWorkoutScreen.kt [COMPLETE - VERIFY]

**Current:** 9.4KB (113% of parent's 8.3KB)
**Status:** Appears complete, needs verification

**Verification Checklist:**
- [ ] Real-time metrics display (load, velocity, power)
- [ ] Rep counter display
- [ ] Set progress indicator
- [ ] Pause/Resume functionality
- [ ] End workout button
- [ ] Safety event handling
- [ ] PR celebration trigger

**Estimated Effort:** LOW (0.5-1 day for verification)

---

### 1.4 ExerciseEditDialog.kt [STUB]

**Current:** 2.1KB (5% of parent's 46KB)
**Status:** Only empty ModalBottomSheet

**Missing Features:**
- [ ] Exercise name/search
- [ ] Muscle group filter
- [ ] Equipment type filter
- [ ] Weight configuration
- [ ] Rep range settings
- [ ] Rest timer settings
- [ ] Notes field
- [ ] Video preview
- [ ] Save/Cancel actions

**Dependencies:**
- ExerciseRepository
- Exercise domain model
- Video player component (expect/actual)

**Estimated Effort:** HIGH (2-3 days)

---

## Phase 2: Routine & Program Management

### 2.1 RoutinesTab.kt [STUB]

**Current:** 1.6KB (8% of parent's 20KB)
**Status:** Only placeholder with TODO

**Missing Features:**
- [ ] Routine list with cards
- [ ] Create new routine button
- [ ] Edit routine functionality
- [ ] Delete routine with confirmation
- [ ] Routine details expansion
- [ ] Exercise count per routine
- [ ] Estimated duration display
- [ ] Start routine button

**Dependencies:**
- Routine domain model
- RoutineRepository
- RoutineBuilderDialog

**Estimated Effort:** MEDIUM (2-3 days)

---

### 2.2 RoutineBuilderDialog.kt [STUB]

**Current:** 1.4KB (6% of parent's 24KB)
**Status:** Only empty Box component

**Missing Features:**
- [ ] Routine name input
- [ ] Exercise list with drag-to-reorder
- [ ] Add exercise button
- [ ] Remove exercise button
- [ ] Per-exercise configuration (sets, reps, weight)
- [ ] Rest time between exercises
- [ ] Video preview per exercise
- [ ] Save/Cancel actions
- [ ] Validation

**Dependencies:**
- ExercisePickerDialog
- ExerciseRepository
- Drag-and-drop implementation

**Estimated Effort:** HIGH (3-4 days)

---

### 2.3 WeeklyProgramsScreen.kt [STUB]

**Current:** 2.8KB (10% of parent's 27KB)
**Status:** Only placeholder with TODO

**Missing Features:**
- [ ] Program list display
- [ ] Create program button
- [ ] Edit/Delete program
- [ ] 7-day week view
- [ ] Routine assignment per day
- [ ] Program progress tracking
- [ ] Active program indicator

**Dependencies:**
- Program domain model
- ProgramRepository
- Calendar/week view component

**Estimated Effort:** MEDIUM (2-3 days)

---

### 2.4 ProgramBuilderScreen.kt [STUB]

**Current:** 3.1KB (14% of parent's 22KB)
**Status:** Only placeholder UI with TODO comments

**Missing Features:**
- [ ] Program name input
- [ ] Day-of-week selector (Mon-Sun)
- [ ] Routine picker per day
- [ ] Rest day toggle
- [ ] Program duration (weeks)
- [ ] Progression settings
- [ ] Save/Cancel actions

**Dependencies:**
- RoutinesTab (to select routines)
- WeeklyProgramsScreen

**Estimated Effort:** MEDIUM (2 days)

---

### 2.5 DailyRoutinesScreen.kt [COMPLETE - VERIFY]

**Current:** 4.4KB (100% of parent)
**Status:** Identical implementation, wraps RoutinesTab

**Verification Checklist:**
- [ ] Displays today's assigned routine
- [ ] Shows routine exercises
- [ ] Start workout button
- [ ] Skip day option

**Estimated Effort:** LOW (0.5 day for verification)

---

## Phase 3: Analytics & History

### 3.1 AnalyticsScreen.kt [PARTIAL]

**Current:** 24KB (44% of parent's 55KB)
**Status:** Core tabs implemented but reduced complexity

**Missing Features:**
- [ ] Full chart implementations (MPAndroidChart replacement)
- [ ] Muscle balance radar chart
- [ ] Volume trend line chart
- [ ] PR progression chart
- [ ] Exercise breakdown pie chart
- [ ] Date range selector
- [ ] Export functionality
- [ ] Comparison mode (week vs week)

**Dependencies:**
- Charts migration (see UI_COMPONENTS_MIGRATION_REPORT.md)
- kotlinx-datetime
- Vico charts or custom Canvas implementations

**Estimated Effort:** HIGH (4-5 days)

---

### 3.2 HistoryAndSettingsTabs.kt [COMPLETE - VERIFY]

**Current:** 65KB (100% of parent)
**Status:** Fully ported

**Verification Checklist:**
- [ ] History tab with workout sessions
- [ ] Session detail expansion
- [ ] Settings tab with all preferences
- [ ] Theme toggle
- [ ] Unit preferences (kg/lbs)
- [ ] Sound/haptic toggles
- [ ] Data export
- [ ] About section

**Estimated Effort:** LOW (0.5 day for verification)

---

### 3.3 InsightsTab.kt [COMPLETE - VERIFY]

**Current:** 5.5KB (100% of parent)
**Status:** Fully implemented

**Verification Checklist:**
- [ ] AI-generated insights display
- [ ] Trend cards
- [ ] Recommendations
- [ ] Achievement highlights

**Estimated Effort:** LOW (0.5 day for verification)

---

### 3.4 ConnectionLogsScreen.kt [STUB]

**Current:** 1.2KB (7% of parent's 17KB)
**Status:** Only placeholder UI

**Missing Features:**
- [ ] Log entry list with timestamps
- [ ] Log level filtering (info, warning, error)
- [ ] Search/filter functionality
- [ ] Export logs button
- [ ] Clear logs button
- [ ] Connection statistics summary
- [ ] Device history

**Dependencies:**
- ConnectionLogsViewModel (needs full implementation)
- ConnectionLogEntity/Repository
- Platform-specific logging

**Estimated Effort:** MEDIUM (2 days)

---

## Phase 4: Supporting Screens

### 4.1 HomeScreen.kt [PARTIAL]

**Current:** 8.7KB (44% of parent's 20KB)
**Status:** Core functionality present but UI simplified

**Missing Features:**
- [ ] Welcome message with user name
- [ ] Today's workout summary card
- [ ] Weekly streak display
- [ ] Quick action buttons
- [ ] Recent PRs highlight
- [ ] Upcoming routine preview

**Dependencies:**
- User preferences
- Workout statistics

**Estimated Effort:** MEDIUM (1-2 days)

---

### 4.2 SingleExerciseScreen.kt [PARTIAL]

**Current:** 3.2KB (25% of parent's 13KB)
**Status:** Basic structure present

**Missing Features:**
- [ ] Full exercise details display
- [ ] Exercise video player
- [ ] Muscle group visualization
- [ ] Exercise history for this exercise
- [ ] PR display for this exercise
- [ ] Start quick workout button

**Dependencies:**
- Video player (expect/actual)
- Exercise history queries

**Estimated Effort:** MEDIUM (1-2 days)

---

### 4.3 CountdownCard.kt [COMPLETE]

**Current:** 14KB (452% of parent's 3.1KB)
**Status:** Fully implemented with expanded animations

**No action required.**

---

### 4.4 RestTimerCard.kt [COMPLETE]

**Current:** 14KB (100% of parent)
**Status:** Fully implemented

**No action required.**

---

## Phase 5: Missing Screens (Need Creation)

### 5.1 EnhancedMainScreen.kt [MISSING]

**Parent Size:** 28KB
**Status:** No KMP equivalent exists

**Description:** Enhanced version of main screen with improved UX

**Features to Implement:**
- [ ] Bottom navigation (Workout, Analytics, Settings)
- [ ] Connection status in app bar
- [ ] Screen transitions
- [ ] Deep link handling
- [ ] State preservation on rotation

**Dependencies:**
- Navigation setup
- All tab screens

**Estimated Effort:** MEDIUM (2 days)

---

### 5.2 LargeSplashScreen.kt [MISSING]

**Parent Size:** 1.8KB
**Status:** No KMP equivalent exists

**Features to Implement:**
- [ ] App logo animation
- [ ] Loading indicator
- [ ] Version display
- [ ] Transition to main screen

**Dependencies:** None

**Estimated Effort:** LOW (0.5 day)

---

### 5.3 HapticFeedbackEffect.kt [MISSING - Moved to Components]

**Parent Size:** 8.2KB
**Status:** Partial expect/actual exists in components

**Note:** This has been partially migrated as an expect/actual in the components folder. Need to verify completeness.

**Verification:**
- [ ] Check `shared/src/commonMain/.../components/HapticFeedbackEffect.kt`
- [ ] Check `shared/src/androidMain/.../components/HapticFeedbackEffect.android.kt`
- [ ] Check `shared/src/desktopMain/.../components/HapticFeedbackEffect.desktop.kt`

**Estimated Effort:** LOW (0.5 day to verify/complete)

---

## Component Dependencies

Based on `UI_COMPONENTS_MIGRATION_REPORT.md`, these components need attention:

### High Priority (Block Screen Implementation)

| Component | Status | Blocking |
|-----------|--------|----------|
| AnalyticsCharts.kt | NOT MIGRATED | AnalyticsScreen |
| ExercisePickerDialog.kt | NOT MIGRATED | JustLiftScreen, RoutineBuilder |
| ExercisePRTracker.kt | NOT MIGRATED | AnalyticsScreen |
| SetSummaryCard.kt | NOT MIGRATED | ActiveWorkoutScreen |
| ImprovedInsightsComponents.kt | NOT MIGRATED | InsightsTab |

### Medium Priority (Enhance UX)

| Component | Status | Affects |
|-----------|--------|---------|
| DashboardComponents.kt | MIGRATED (needs kotlinx-datetime) | HomeScreen |
| Video Player | NOT IMPLEMENTED (expect/actual needed) | ExercisePickerDialog |

### Library Dependencies Required

1. **kotlinx-datetime** - Required for DashboardComponents, date formatting
2. **Vico Charts OR Custom Canvas** - Required for analytics charts
3. **KMP Logging (Kermit)** - Replace Timber throughout

---

## Implementation Order (Recommended)

### Sprint 1: Foundation (Week 1)
1. Add kotlinx-datetime dependency
2. Add Kermit logging dependency
3. Verify complete screens (ActiveWorkout, CountdownCard, RestTimerCard)
4. Implement LargeSplashScreen

### Sprint 2: Core Workout (Week 2)
1. Complete WorkoutTab.kt
2. Enhance JustLiftScreen.kt
3. Implement ExerciseEditDialog.kt
4. Port ExercisePickerDialog component

### Sprint 3: Routine Management (Week 3)
1. Complete RoutinesTab.kt
2. Implement RoutineBuilderDialog.kt
3. Complete WeeklyProgramsScreen.kt
4. Complete ProgramBuilderScreen.kt

### Sprint 4: Analytics & Polish (Week 4)
1. Complete AnalyticsScreen.kt (with chart implementations)
2. Enhance HomeScreen.kt
3. Complete ConnectionLogsScreen.kt
4. Implement EnhancedMainScreen.kt

### Sprint 5: QA & Refinement (Week 5)
1. Full regression testing
2. UI polish and animations
3. Performance optimization
4. Platform-specific testing (Android, Desktop, iOS)

---

## Success Criteria

Each screen is considered "at parity" when:

1. **Visual Match:** UI layout matches parent project
2. **Feature Complete:** All interactive elements function identically
3. **State Management:** All state transitions work correctly
4. **Error Handling:** Error states display appropriately
5. **Accessibility:** Basic accessibility support maintained
6. **Performance:** No noticeable lag or jank

---

## Progress Tracking

| Screen | Phase | Status | Assignee | PR |
|--------|-------|--------|----------|-----|
| WorkoutTab | 1 | NOT STARTED | | |
| JustLiftScreen | 1 | PARTIAL | | |
| ActiveWorkoutScreen | 1 | VERIFY | | |
| ExerciseEditDialog | 1 | NOT STARTED | | |
| RoutinesTab | 2 | NOT STARTED | | |
| RoutineBuilderDialog | 2 | NOT STARTED | | |
| WeeklyProgramsScreen | 2 | NOT STARTED | | |
| ProgramBuilderScreen | 2 | NOT STARTED | | |
| DailyRoutinesScreen | 2 | VERIFY | | |
| AnalyticsScreen | 3 | PARTIAL | | |
| HistoryAndSettingsTabs | 3 | VERIFY | | |
| InsightsTab | 3 | VERIFY | | |
| ConnectionLogsScreen | 3 | NOT STARTED | | |
| HomeScreen | 4 | PARTIAL | | |
| SingleExerciseScreen | 4 | PARTIAL | | |
| CountdownCard | 4 | COMPLETE | | |
| RestTimerCard | 4 | COMPLETE | | |
| EnhancedMainScreen | 5 | NOT STARTED | | |
| LargeSplashScreen | 5 | NOT STARTED | | |
| HapticFeedbackEffect | 5 | VERIFY | | |

---

## Notes

- All screen implementations should follow KMP best practices
- Use expect/actual for platform-specific functionality
- Maintain consistent error handling patterns
- Add unit tests for ViewModels as they're implemented
- Document any intentional deviations from parent project

---

*Document generated: 2025-11-26*
*Based on: PROJECT_COMPARISON.md, UI_COMPONENTS_MIGRATION_REPORT.md*
