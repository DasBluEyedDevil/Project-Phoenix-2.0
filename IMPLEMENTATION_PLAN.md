# Project Phoenix 2.0 - Enhancement Implementation Plan

This document outlines the comprehensive implementation plan for the stakeholder enhancement requests.

---

## Table of Contents
1. [Custom Exercises to Library](#1-custom-exercises-to-library)
2. [Gamification - Badges and Streaks](#2-gamification---badges-and-streaks)
3. [Adjust Weight During Routine](#3-adjust-weight-during-routine)
4. [Superset Creation](#4-superset-creation)
5. [Skip/Go Back in Routine](#5-skipgo-back-in-routine)
6. [Echo Mode Average Weight Display](#6-echo-mode-average-weight-display)
7. [Export Date Range Selection](#7-export-date-range-selection)

---

## 1. Custom Exercises to Library

### Overview
Allow users to create custom exercises that appear alongside the pre-loaded exercise library.

### Current State Analysis
- **Database**: Already has `isCustom INTEGER NOT NULL DEFAULT 0` column in `Exercise` table (`VitruvianDatabase.sq:13`)
- **Model**: `Exercise` data class in `domain/model/Exercise.kt` supports all needed fields
- **Repository**: `ExerciseRepository` interface in `data/repository/ExerciseRepository.kt` has `getAllExercises()`, but no create/update methods for custom exercises

### Implementation Steps

#### Step 1: Update Database Schema
**File**: `shared/src/commonMain/sqldelight/com/devil/phoenixproject/database/VitruvianDatabase.sq`

Add new queries:
```sql
-- Custom Exercise Queries
selectCustomExercises:
SELECT * FROM Exercise WHERE isCustom = 1 ORDER BY name ASC;

insertCustomExercise:
INSERT INTO Exercise (id, name, muscleGroup, muscleGroups, equipment, defaultCableConfig, isFavorite, isCustom)
VALUES (?, ?, ?, ?, ?, ?, 0, 1);

updateExercise:
UPDATE Exercise SET name = ?, muscleGroup = ?, muscleGroups = ?, equipment = ?, defaultCableConfig = ?
WHERE id = ? AND isCustom = 1;

deleteCustomExercise:
DELETE FROM Exercise WHERE id = ? AND isCustom = 1;
```

#### Step 2: Update ExerciseRepository Interface
**File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/ExerciseRepository.kt`

Add methods:
```kotlin
suspend fun createCustomExercise(exercise: Exercise): Result<Exercise>
suspend fun updateCustomExercise(exercise: Exercise): Result<Exercise>
suspend fun deleteCustomExercise(exerciseId: String): Result<Unit>
fun getCustomExercises(): Flow<List<Exercise>>
```

#### Step 3: Implement in SqlDelightExerciseRepository
**File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/SqlDelightExerciseRepository.kt`

Implement the new interface methods using the SQLDelight generated queries.

#### Step 4: Create Custom Exercise UI
**New File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/presentation/screen/CreateExerciseDialog.kt`

Components needed:
- Exercise name input (required)
- Primary muscle group selector (dropdown with predefined options)
- Secondary muscle groups selector (multi-select chips)
- Equipment type selector (Single Cable, Double Cable, Either)
- Default cable configuration selector

#### Step 5: Add Entry Point in Exercise Library
**File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/presentation/screen/RoutinesTab.kt` (or ExerciseLibraryScreen)

- Add FAB or "Add Custom Exercise" button
- Add edit/delete actions for custom exercises (show three-dot menu for custom exercises only)
- Visually distinguish custom exercises (e.g., badge, different icon)

### Dependencies
- None (uses existing infrastructure)

### Effort Estimate
- Database: Low
- Repository: Low
- UI: Medium
- Testing: Medium
- **Total: Medium**

---

## 2. Gamification - Badges and Streaks

### Overview
Add a comprehensive gamification system with badges for achievements and streak tracking for workout consistency.

### Current State Analysis
- **Streak Tracking**: Already implemented in `MainViewModel.kt:274-304` - calculates consecutive workout days
- **PR Celebration**: Already has `PRCelebrationEvent` with SharedFlow broadcasting
- **Progress Tracking**: Volume progression calculated between sessions

### Implementation Steps

#### Step 1: Define Badge System Domain Models
**New File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/domain/model/Gamification.kt`

```kotlin
// Badge categories
enum class BadgeCategory {
    CONSISTENCY,    // Streak-based badges
    STRENGTH,       // PR and weight milestones
    VOLUME,         // Total reps/volume milestones
    EXPLORER,       // Exercise variety badges
    DEDICATION      // Long-term commitment badges
}

// Badge definitions
data class Badge(
    val id: String,
    val name: String,
    val description: String,
    val category: BadgeCategory,
    val iconResource: String,      // Icon identifier
    val tier: BadgeTier,           // Bronze, Silver, Gold, Platinum
    val requirement: BadgeRequirement,
    val isSecret: Boolean = false  // Hidden until earned
)

enum class BadgeTier { BRONZE, SILVER, GOLD, PLATINUM }

sealed class BadgeRequirement {
    data class StreakDays(val days: Int) : BadgeRequirement()
    data class TotalWorkouts(val count: Int) : BadgeRequirement()
    data class TotalReps(val count: Int) : BadgeRequirement()
    data class PRsAchieved(val count: Int) : BadgeRequirement()
    data class UniqueExercises(val count: Int) : BadgeRequirement()
    data class WorkoutsInWeek(val count: Int) : BadgeRequirement()
    data class ConsecutiveWeeks(val weeks: Int) : BadgeRequirement()
    data class TotalVolume(val kgLifted: Long) : BadgeRequirement()
    data class SingleWorkoutVolume(val kgLifted: Int) : BadgeRequirement()
}

// User's earned badge
data class EarnedBadge(
    val id: Long = 0,
    val badgeId: String,
    val earnedAt: Long,
    val celebratedAt: Long? = null  // Track if user has seen the celebration
)

// Streak data
data class StreakInfo(
    val currentStreak: Int,
    val longestStreak: Int,
    val streakStartDate: Long?,
    val lastWorkoutDate: Long?
)
```

#### Step 2: Create Database Tables
**File**: `shared/src/commonMain/sqldelight/com/devil/phoenixproject/database/VitruvianDatabase.sq`

```sql
-- Gamification Tables
CREATE TABLE EarnedBadge (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    badgeId TEXT NOT NULL UNIQUE,
    earnedAt INTEGER NOT NULL,
    celebratedAt INTEGER
);

CREATE TABLE StreakHistory (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    startDate INTEGER NOT NULL,
    endDate INTEGER NOT NULL,
    length INTEGER NOT NULL
);

CREATE TABLE GamificationStats (
    id INTEGER PRIMARY KEY,
    totalWorkouts INTEGER NOT NULL DEFAULT 0,
    totalReps INTEGER NOT NULL DEFAULT 0,
    totalVolumeKg INTEGER NOT NULL DEFAULT 0,
    longestStreak INTEGER NOT NULL DEFAULT 0,
    uniqueExercisesUsed INTEGER NOT NULL DEFAULT 0,
    prsAchieved INTEGER NOT NULL DEFAULT 0,
    lastUpdated INTEGER NOT NULL
);

-- Queries
selectAllEarnedBadges:
SELECT * FROM EarnedBadge ORDER BY earnedAt DESC;

selectUncelebratedBadges:
SELECT * FROM EarnedBadge WHERE celebratedAt IS NULL;

insertEarnedBadge:
INSERT OR IGNORE INTO EarnedBadge (badgeId, earnedAt) VALUES (?, ?);

markBadgeCelebrated:
UPDATE EarnedBadge SET celebratedAt = ? WHERE badgeId = ?;

selectGamificationStats:
SELECT * FROM GamificationStats WHERE id = 1;

upsertGamificationStats:
INSERT OR REPLACE INTO GamificationStats
(id, totalWorkouts, totalReps, totalVolumeKg, longestStreak, uniqueExercisesUsed, prsAchieved, lastUpdated)
VALUES (1, ?, ?, ?, ?, ?, ?, ?);
```

#### Step 3: Create Badge Definitions
**New File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/local/BadgeDefinitions.kt`

Define all badges with requirements:
```kotlin
object BadgeDefinitions {
    val allBadges: List<Badge> = listOf(
        // Consistency Badges
        Badge("streak_3", "Getting Started", "3-day workout streak", CONSISTENCY, "fire", BRONZE, StreakDays(3)),
        Badge("streak_7", "Week Warrior", "7-day workout streak", CONSISTENCY, "fire", SILVER, StreakDays(7)),
        Badge("streak_30", "Monthly Dedication", "30-day workout streak", CONSISTENCY, "fire", GOLD, StreakDays(30)),
        Badge("streak_100", "Centurion", "100-day workout streak", CONSISTENCY, "fire", PLATINUM, StreakDays(100)),

        // Workout Count Badges
        Badge("workouts_10", "First Steps", "Complete 10 workouts", DEDICATION, "dumbbell", BRONZE, TotalWorkouts(10)),
        Badge("workouts_50", "Regular", "Complete 50 workouts", DEDICATION, "dumbbell", SILVER, TotalWorkouts(50)),
        Badge("workouts_100", "Committed", "Complete 100 workouts", DEDICATION, "dumbbell", GOLD, TotalWorkouts(100)),
        Badge("workouts_500", "Iron Will", "Complete 500 workouts", DEDICATION, "dumbbell", PLATINUM, TotalWorkouts(500)),

        // PR Badges
        Badge("pr_1", "Personal Best", "Achieve your first PR", STRENGTH, "trophy", BRONZE, PRsAchieved(1)),
        Badge("pr_10", "Record Breaker", "Achieve 10 PRs", STRENGTH, "trophy", SILVER, PRsAchieved(10)),
        Badge("pr_50", "PR Machine", "Achieve 50 PRs", STRENGTH, "trophy", GOLD, PRsAchieved(50)),

        // Volume Badges
        Badge("reps_1000", "Rep Rookie", "Complete 1,000 reps", VOLUME, "repeat", BRONZE, TotalReps(1000)),
        Badge("reps_10000", "Rep Master", "Complete 10,000 reps", VOLUME, "repeat", SILVER, TotalReps(10000)),
        Badge("reps_100000", "Rep Legend", "Complete 100,000 reps", VOLUME, "repeat", GOLD, TotalReps(100000)),

        // Explorer Badges
        Badge("exercises_5", "Curious", "Try 5 different exercises", EXPLORER, "compass", BRONZE, UniqueExercises(5)),
        Badge("exercises_20", "Adventurer", "Try 20 different exercises", EXPLORER, "compass", SILVER, UniqueExercises(20)),
        Badge("exercises_50", "Explorer", "Try 50 different exercises", EXPLORER, "compass", GOLD, UniqueExercises(50)),

        // Secret Badges (revealed only when earned)
        Badge("early_bird", "Early Bird", "Complete a workout before 6 AM", DEDICATION, "sun", GOLD, WorkoutsInWeek(1), isSecret = true),
        Badge("night_owl", "Night Owl", "Complete a workout after 10 PM", DEDICATION, "moon", GOLD, WorkoutsInWeek(1), isSecret = true),
    )
}
```

#### Step 4: Create GamificationRepository
**New File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/GamificationRepository.kt`

```kotlin
interface GamificationRepository {
    fun getEarnedBadges(): Flow<List<EarnedBadge>>
    fun getStreakInfo(): Flow<StreakInfo>
    fun getGamificationStats(): Flow<GamificationStats>
    suspend fun awardBadge(badgeId: String)
    suspend fun markBadgeCelebrated(badgeId: String)
    suspend fun updateStats()
    suspend fun checkAndAwardBadges(): List<Badge>  // Returns newly awarded badges
}
```

#### Step 5: Create Badge Checking Use Case
**New File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/domain/usecase/BadgeCheckUseCase.kt`

Logic to check all badge requirements against current stats and award new badges.

#### Step 6: Create Gamification UI Components

**New File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/presentation/screen/BadgesScreen.kt`
- Display all badges (earned and locked)
- Show progress toward next badges
- Badge details on tap

**New File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/presentation/components/StreakWidget.kt`
- Fire emoji + streak count
- Animated flame effect for active streaks
- Shows "at risk" state if no workout today

**New File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/presentation/components/BadgeCelebrationDialog.kt`
- Animated celebration when badge earned
- Confetti effect
- Badge icon and description

#### Step 7: Integrate into HomeScreen
**File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/presentation/screen/HomeScreen.kt`

- Add StreakWidget to show current streak prominently
- Add "Recent Badges" section showing latest earned badges
- Add navigation to full Badges screen

#### Step 8: Add Badge Notifications
Integrate with existing `HapticEvent` system:
- Add `BADGE_EARNED` haptic event
- Trigger celebration after workout completion if new badge earned

### Dependencies
- Existing streak calculation in MainViewModel
- Existing PR celebration infrastructure

### Effort Estimate
- Domain Models: Low
- Database: Medium
- Repository + Use Case: Medium
- UI Components: High
- Integration: Medium
- **Total: High**

---

## 3. Adjust Weight During Routine

### Overview
Allow users to modify the weight of exercises while actively going through a routine, rather than being locked to pre-configured values.

### Current State Analysis
- **RoutineExercise Model**: Already supports `setWeightsPerCableKg: List<Float>` for per-set weights (`Routine.kt:34`)
- **Workout Flow**: Weight is set when loading exercise from routine (`MainViewModel.kt:1418-1421`)
- **No Runtime Override**: Currently no mechanism to override weight mid-routine

### Implementation Steps

#### Step 1: Add Weight Override State to MainViewModel
**File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/presentation/viewmodel/MainViewModel.kt`

```kotlin
// Add state for runtime weight override
private val _weightOverride = MutableStateFlow<Float?>(null)
val weightOverride: StateFlow<Float?> = _weightOverride.asStateFlow()

// Add method to override weight
fun overrideWeight(newWeightKg: Float) {
    _weightOverride.value = newWeightKg
    // Update workout parameters with new weight
    _workoutParameters.update { params ->
        params.copy(weightPerCableKg = newWeightKg)
    }
    // Send updated weight to machine via BLE
    sendWeightUpdateCommand(newWeightKg)
}

// Reset override when moving to next exercise
private fun resetWeightOverride() {
    _weightOverride.value = null
}
```

#### Step 2: Add Weight Adjustment UI to WorkoutTab
**File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/presentation/screen/WorkoutTab.kt`

Add weight adjustment controls:
```kotlin
// In the Active workout state display:
@Composable
fun WeightAdjustmentControls(
    currentWeightKg: Float,
    onWeightChange: (Float) -> Unit,
    weightUnit: WeightUnit,
    formatWeight: (Float) -> String
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Decrease button (-0.5kg / -1lb)
        IconButton(onClick = { onWeightChange(currentWeightKg - 0.5f) }) {
            Icon(Icons.Default.Remove, "Decrease weight")
        }

        // Current weight display (tappable for direct input)
        Text(
            text = formatWeight(currentWeightKg),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.clickable { /* Open weight picker dialog */ }
        )

        // Increase button (+0.5kg / +1lb)
        IconButton(onClick = { onWeightChange(currentWeightKg + 0.5f) }) {
            Icon(Icons.Default.Add, "Increase weight")
        }
    }
}
```

#### Step 3: Update BLE Communication
**File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/util/BlePacketFactory.kt`

Ensure there's a method to send weight updates mid-workout:
```kotlin
fun createWeightUpdatePacket(weightPerCableKg: Float): ByteArray
```

#### Step 4: Add Quick Weight Presets
During rest periods or before starting a set, show quick adjustment options:
- Last used weight for this exercise
- PR weight for this exercise
- +/- 5% buttons for quick adjustments

#### Step 5: Track Weight Changes in Session
Update `WorkoutSession` to optionally store actual weight used (if different from planned):
```kotlin
// In WorkoutSession
val plannedWeightKg: Float? = null,  // Original routine weight
val actualWeightKg: Float = weightPerCableKg  // Weight actually used
```

### Dependencies
- BLE protocol support for mid-workout weight changes (verify with machine)

### Effort Estimate
- ViewModel Logic: Low
- UI Components: Medium
- BLE Integration: Medium (depends on protocol verification)
- **Total: Medium**

---

## 4. Superset Creation

### Overview
Allow users to combine multiple exercises into a superset within routines, where exercises in a superset are performed back-to-back with minimal rest.

### Current State Analysis
- **Routine Model**: `Routine` has `exercises: List<RoutineExercise>` with `orderIndex` (`Routine.kt:10`)
- **RoutineExercise**: Has `setRestSeconds` for per-set rest times (`Routine.kt:41`)
- **No Grouping**: Currently no concept of exercise grouping/supersets

### Implementation Steps

#### Step 1: Add Superset Support to Domain Model
**File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/domain/model/Routine.kt`

```kotlin
// Add superset group ID to RoutineExercise
data class RoutineExercise(
    val id: String,
    val exercise: Exercise,
    // ... existing fields ...
    val supersetGroupId: String? = null,  // Exercises with same ID are in same superset
    val supersetOrder: Int = 0,           // Order within the superset
    val supersetRestSeconds: Int = 10     // Rest between superset exercises (default 10s)
)

// Helper data class for UI
data class SupersetGroup(
    val id: String,
    val name: String,          // e.g., "Superset A", "Superset B"
    val exercises: List<RoutineExercise>
)

// Extension to get grouped exercises
fun Routine.getExercisesGrouped(): List<RoutineItem> {
    val supersets = exercises.filter { it.supersetGroupId != null }
        .groupBy { it.supersetGroupId }
        .map { (id, exercises) ->
            SupersetItem(
                SupersetGroup(
                    id = id!!,
                    name = "Superset ${('A'.code + (exercises.first().orderIndex / 10)).toChar()}",
                    exercises = exercises.sortedBy { it.supersetOrder }
                )
            )
        }
    val singles = exercises.filter { it.supersetGroupId == null }
        .map { SingleExerciseItem(it) }

    return (supersets + singles).sortedBy {
        when (it) {
            is SupersetItem -> it.group.exercises.first().orderIndex
            is SingleExerciseItem -> it.exercise.orderIndex
        }
    }
}

sealed class RoutineItem {
    data class SingleExerciseItem(val exercise: RoutineExercise) : RoutineItem()
    data class SupersetItem(val group: SupersetGroup) : RoutineItem()
}
```

#### Step 2: Update Database Schema
**File**: `shared/src/commonMain/sqldelight/com/devil/phoenixproject/database/VitruvianDatabase.sq`

```sql
-- Add superset columns to RoutineExercise
ALTER TABLE RoutineExercise ADD COLUMN supersetGroupId TEXT;
ALTER TABLE RoutineExercise ADD COLUMN supersetOrder INTEGER NOT NULL DEFAULT 0;
ALTER TABLE RoutineExercise ADD COLUMN supersetRestSeconds INTEGER NOT NULL DEFAULT 10;
```

#### Step 3: Update Routine Builder UI
**File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/presentation/screen/RoutineBuilderDialog.kt`

Add superset creation UI:
- Drag-and-drop exercises into superset groups
- Visual grouping with connecting bracket/line
- "Create Superset" button when multiple exercises selected
- "Break Superset" option to ungroup
- Reorder exercises within superset

```kotlin
@Composable
fun SupersetCard(
    superset: SupersetGroup,
    onReorder: (List<RoutineExercise>) -> Unit,
    onBreakSuperset: () -> Unit,
    onEditExercise: (RoutineExercise) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column {
            // Superset header
            Row {
                Icon(Icons.Default.Repeat, "Superset")
                Text(superset.name)
                IconButton(onClick = onBreakSuperset) {
                    Icon(Icons.Default.CallSplit, "Break superset")
                }
            }
            // Exercises in superset
            superset.exercises.forEach { exercise ->
                SupersetExerciseRow(
                    exercise = exercise,
                    onEdit = { onEditExercise(exercise) }
                )
            }
        }
    }
}
```

#### Step 4: Update Workout Execution Logic
**File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/presentation/viewmodel/MainViewModel.kt`

Modify `advanceToNextExercise()` and rest timer logic:
```kotlin
private fun advanceToNextExercise() {
    val routine = _loadedRoutine.value ?: return
    val currentExercise = routine.exercises.getOrNull(_currentExerciseIndex.value) ?: return

    // Check if current exercise is part of a superset
    val supersetId = currentExercise.supersetGroupId
    if (supersetId != null) {
        val supersetExercises = routine.exercises.filter { it.supersetGroupId == supersetId }
        val currentSupersetIndex = supersetExercises.indexOfFirst { it.id == currentExercise.id }

        if (currentSupersetIndex < supersetExercises.size - 1) {
            // More exercises in superset - use short superset rest
            val nextInSuperset = supersetExercises[currentSupersetIndex + 1]
            _currentExerciseIndex.value = routine.exercises.indexOf(nextInSuperset)
            startRestTimer(currentExercise.supersetRestSeconds)
            return
        }
        // Superset complete - check for more sets
        // ... handle superset set completion
    }

    // Regular exercise progression
    // ... existing logic
}
```

#### Step 5: Update Rest Timer Display
Show context-aware rest information:
- "Superset Rest - 10s" for between superset exercises
- "Next: [Exercise Name] (Superset)" to indicate more superset exercises coming
- Visual indicator of superset progress (e.g., "Exercise 2/3 in Superset A")

### Dependencies
- Database migration for new columns

### Effort Estimate
- Domain Model: Medium
- Database: Low
- Routine Builder UI: High
- Workout Execution Logic: High
- **Total: High**

---

## 5. Skip/Go Back in Routine

### Overview
Allow users to skip ahead or go back to any exercise while executing a routine, rather than being forced to complete exercises in order.

### Current State Analysis
- **Current Index**: `_currentExerciseIndex` tracks position in routine (`MainViewModel.kt:195`)
- **Linear Progression**: `advanceToNextExercise()` only increments index (`MainViewModel.kt:1414-1440`)
- **No Random Access**: No methods to jump to arbitrary exercise index

### Implementation Steps

#### Step 1: Add Navigation Methods to MainViewModel
**File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/presentation/viewmodel/MainViewModel.kt`

```kotlin
// Navigate to specific exercise in routine
fun jumpToExercise(index: Int) {
    val routine = _loadedRoutine.value ?: return
    if (index < 0 || index >= routine.exercises.size) return

    // End current exercise if active
    if (_workoutState.value is WorkoutState.Active) {
        saveCurrentExerciseProgress()
    }

    _currentExerciseIndex.value = index
    _currentSetIndex.value = 0

    // Load new exercise parameters
    val exercise = routine.exercises[index]
    updateWorkoutParametersFromRoutineExercise(exercise)

    // Reset to countdown state for new exercise
    _workoutState.value = WorkoutState.Idle
}

// Skip to next exercise
fun skipCurrentExercise() {
    val routine = _loadedRoutine.value ?: return
    val nextIndex = _currentExerciseIndex.value + 1
    if (nextIndex < routine.exercises.size) {
        jumpToExercise(nextIndex)
    }
}

// Go back to previous exercise
fun goToPreviousExercise() {
    val prevIndex = _currentExerciseIndex.value - 1
    if (prevIndex >= 0) {
        jumpToExercise(prevIndex)
    }
}

// Mark exercise as skipped (for tracking)
private val _skippedExercises = MutableStateFlow<Set<Int>>(emptySet())
val skippedExercises: StateFlow<Set<Int>> = _skippedExercises.asStateFlow()

private fun saveCurrentExerciseProgress() {
    // Save partial progress if any reps completed
    val repCount = _repCount.value
    if (repCount.workingReps > 0) {
        saveSession(isPartial = true)
    } else {
        // Mark as skipped
        _skippedExercises.update { it + _currentExerciseIndex.value }
    }
}
```

#### Step 2: Add Exercise Navigator UI Component
**New File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/presentation/components/ExerciseNavigator.kt`

```kotlin
@Composable
fun ExerciseNavigator(
    currentIndex: Int,
    totalExercises: Int,
    exerciseNames: List<String>,
    skippedIndices: Set<Int>,
    completedIndices: Set<Int>,
    onNavigateToExercise: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Progress indicator with tappable dots
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            exerciseNames.forEachIndexed { index, name ->
                ExerciseDot(
                    index = index,
                    name = name,
                    isCurrent = index == currentIndex,
                    isCompleted = index in completedIndices,
                    isSkipped = index in skippedIndices,
                    onClick = { onNavigateToExercise(index) }
                )
            }
        }

        // Navigation buttons
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Previous button
            IconButton(
                onClick = { onNavigateToExercise(currentIndex - 1) },
                enabled = currentIndex > 0
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous exercise")
            }

            // Exercise list dropdown
            ExerciseListDropdown(
                exercises = exerciseNames,
                currentIndex = currentIndex,
                onSelect = onNavigateToExercise
            )

            // Next/Skip button
            IconButton(
                onClick = { onNavigateToExercise(currentIndex + 1) },
                enabled = currentIndex < totalExercises - 1
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next exercise")
            }
        }
    }
}

@Composable
fun ExerciseDot(
    index: Int,
    name: String,
    isCurrent: Boolean,
    isCompleted: Boolean,
    isSkipped: Boolean,
    onClick: () -> Unit
) {
    val color = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        isCompleted -> MaterialTheme.colorScheme.tertiary
        isSkipped -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Box(
        modifier = Modifier
            .size(if (isCurrent) 16.dp else 12.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isCompleted) {
            Icon(Icons.Default.Check, null, Modifier.size(8.dp))
        }
    }
}
```

#### Step 3: Integrate Navigator into WorkoutTab
**File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/presentation/screen/WorkoutTab.kt`

Add ExerciseNavigator component:
- Show during Idle, Countdown, and Resting states
- Hide during Active workout state (or show minimized version)
- Allow full navigation during rest periods

#### Step 4: Add Confirmation Dialog for Skip
```kotlin
@Composable
fun SkipExerciseDialog(
    exerciseName: String,
    hasProgress: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Skip Exercise?") },
        text = {
            Column {
                Text("Skip \"$exerciseName\"?")
                if (hasProgress) {
                    Text(
                        "Your progress will be saved.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Skip") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
```

#### Step 5: Update Routine Completion Logic
Handle cases where exercises were skipped:
- Show summary of skipped exercises at routine completion
- Option to go back and complete skipped exercises
- Track skipped exercises in workout history

### Dependencies
- None

### Effort Estimate
- ViewModel Logic: Medium
- Navigator UI: Medium
- Integration: Low
- **Total: Medium**

---

## 6. Echo Mode Average Weight Display

### Overview
When completing an exercise in Echo mode, display the overall average weight used across both concentric and eccentric phases, in addition to the existing phase-specific averages.

### Current State Analysis
- **SetSummary State**: Shows `peakPower` and `averagePower` (`Models.kt:42-47`)
- **WorkoutMetric**: Has `loadA`, `loadB`, `totalLoad` fields (`Models.kt:193-205`)
- **Metric Collection**: Metrics collected during workout but not phase-separated

### Implementation Steps

#### Step 1: Enhance Metric Tracking for Phases
**File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/domain/model/Models.kt`

Add phase-aware metrics:
```kotlin
// Enhanced SetSummary for Echo mode
data class SetSummary(
    val metrics: List<WorkoutMetric>,
    val peakPower: Float,
    val averagePower: Float,
    val repCount: Int,
    // Echo mode specific metrics
    val echoMetrics: EchoSetMetrics? = null
)

data class EchoSetMetrics(
    val averageConcentricLoad: Float,    // Average load during push/pull (up) phase
    val averageEccentricLoad: Float,     // Average load during lowering (down) phase
    val overallAverageLoad: Float,       // Weighted average across both phases
    val peakConcentricLoad: Float,
    val peakEccentricLoad: Float,
    val concentricDuration: Long,        // Total time in concentric phase
    val eccentricDuration: Long,         // Total time in eccentric phase
    val timeUnderTension: Long           // Total active time
)
```

#### Step 2: Add Phase Detection to Rep Counter
**File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/domain/usecase/RepCounterFromMachine.kt`

Enhance to track phase transitions:
```kotlin
enum class MovementPhase {
    CONCENTRIC,    // Moving up/contracting (position decreasing typically)
    ECCENTRIC,     // Moving down/extending (position increasing typically)
    STATIC         // Not moving significantly
}

data class PhaseMetrics(
    val phase: MovementPhase,
    val metrics: MutableList<WorkoutMetric> = mutableListOf()
)

class RepCounterFromMachine {
    private val phaseHistory = mutableListOf<PhaseMetrics>()
    private var currentPhase = MovementPhase.STATIC

    fun processMetricWithPhase(metric: WorkoutMetric): MovementPhase {
        // Detect phase based on position change
        val previousPosition = lastMetric?.positionA ?: metric.positionA
        val positionDelta = metric.positionA - previousPosition

        val newPhase = when {
            positionDelta < -PHASE_THRESHOLD -> MovementPhase.CONCENTRIC
            positionDelta > PHASE_THRESHOLD -> MovementPhase.ECCENTRIC
            else -> currentPhase
        }

        if (newPhase != currentPhase) {
            phaseHistory.add(PhaseMetrics(newPhase))
            currentPhase = newPhase
        }

        phaseHistory.lastOrNull()?.metrics?.add(metric)
        return currentPhase
    }

    fun calculateEchoMetrics(): EchoSetMetrics {
        val concentricMetrics = phaseHistory
            .filter { it.phase == MovementPhase.CONCENTRIC }
            .flatMap { it.metrics }
        val eccentricMetrics = phaseHistory
            .filter { it.phase == MovementPhase.ECCENTRIC }
            .flatMap { it.metrics }

        val avgConcentric = concentricMetrics.map { it.totalLoad }.average().toFloat()
        val avgEccentric = eccentricMetrics.map { it.totalLoad }.average().toFloat()
        val totalMetrics = concentricMetrics + eccentricMetrics
        val overallAvg = totalMetrics.map { it.totalLoad }.average().toFloat()

        return EchoSetMetrics(
            averageConcentricLoad = avgConcentric,
            averageEccentricLoad = avgEccentric,
            overallAverageLoad = overallAvg,
            peakConcentricLoad = concentricMetrics.maxOfOrNull { it.totalLoad } ?: 0f,
            peakEccentricLoad = eccentricMetrics.maxOfOrNull { it.totalLoad } ?: 0f,
            concentricDuration = calculatePhaseDuration(concentricMetrics),
            eccentricDuration = calculatePhaseDuration(eccentricMetrics),
            timeUnderTension = calculatePhaseDuration(totalMetrics)
        )
    }
}
```

#### Step 3: Update SetSummary Display for Echo Mode
**File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/presentation/screen/WorkoutTab.kt` (or SetSummaryCard)

```kotlin
@Composable
fun EchoSetSummaryCard(
    echoMetrics: EchoSetMetrics,
    formatWeight: (Float) -> String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Echo Mode Summary",
                style = MaterialTheme.typography.titleMedium
            )

            // Overall Average (most prominent)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatWeight(echoMetrics.overallAverageLoad),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Overall Average",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider()

            // Phase breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Concentric
                MetricColumn(
                    label = "Concentric",
                    icon = Icons.Default.ArrowUpward,
                    average = formatWeight(echoMetrics.averageConcentricLoad),
                    peak = formatWeight(echoMetrics.peakConcentricLoad)
                )

                // Eccentric
                MetricColumn(
                    label = "Eccentric",
                    icon = Icons.Default.ArrowDownward,
                    average = formatWeight(echoMetrics.averageEccentricLoad),
                    peak = formatWeight(echoMetrics.peakEccentricLoad)
                )
            }

            // Time under tension
            Text(
                "Time Under Tension: ${formatDuration(echoMetrics.timeUnderTension)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

#### Step 4: Store Echo Metrics in Session History
Update `WorkoutSession` to store Echo metrics:
```kotlin
data class WorkoutSession(
    // ... existing fields ...
    val avgConcentricLoadKg: Float? = null,
    val avgEccentricLoadKg: Float? = null,
    val avgOverallLoadKg: Float? = null
)
```

Update database schema accordingly.

#### Step 5: Display in History
Show Echo metrics in workout history detail view for Echo mode sessions.

### Dependencies
- Accurate phase detection requires position data from machine

### Effort Estimate
- Domain Model: Low
- Phase Detection Logic: High
- UI Components: Medium
- Database Updates: Low
- **Total: Medium-High**

---

## 7. Export Date Range Selection

### Overview
Allow users to export workout history for a specific date range, rather than being limited to a fixed number (the "last 20" mentioned may be from original app behavior).

### Current State Analysis
- **Current Export**: Uses `allWorkoutSessions` which exports ALL sessions (`AnalyticsScreen.kt:606-607`)
- **Interface**: `CsvExporter.exportWorkoutHistory()` takes full list (`CsvExporter.kt:37-42`)
- **No Filtering**: No date range parameters currently supported

### Implementation Steps

#### Step 1: Add Date Range Parameters to CsvExporter Interface
**File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/util/CsvExporter.kt`

```kotlin
interface CsvExporter {
    // ... existing methods ...

    /**
     * Export workout history with optional date range filter
     *
     * @param workoutSessions List of workout sessions to export
     * @param startDate Optional start timestamp (inclusive). If null, no lower bound.
     * @param endDate Optional end timestamp (inclusive). If null, no upper bound.
     * @param exerciseNames Map of exercise IDs to display names
     * @param weightUnit Unit to use for weight values
     * @param formatWeight Function to format weight values
     * @return Result containing URI/path to the exported file or error
     */
    fun exportWorkoutHistoryFiltered(
        workoutSessions: List<WorkoutSession>,
        startDate: Long? = null,
        endDate: Long? = null,
        exerciseNames: Map<String, String>,
        weightUnit: WeightUnit,
        formatWeight: (Float, WeightUnit) -> String
    ): Result<String> {
        // Default implementation filters and calls existing method
        val filtered = workoutSessions.filter { session ->
            val afterStart = startDate == null || session.timestamp >= startDate
            val beforeEnd = endDate == null || session.timestamp <= endDate
            afterStart && beforeEnd
        }
        return exportWorkoutHistory(filtered, exerciseNames, weightUnit, formatWeight)
    }
}
```

#### Step 2: Create Date Range Picker Dialog
**New File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/presentation/components/DateRangePickerDialog.kt`

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    onDateRangeSelected: (startDate: Long?, endDate: Long?) -> Unit,
    onDismiss: () -> Unit,
    initialStartDate: Long? = null,
    initialEndDate: Long? = null
) {
    var selectedRange by remember {
        mutableStateOf(DateRangeOption.ALL_TIME)
    }
    var customStartDate by remember { mutableStateOf(initialStartDate) }
    var customEndDate by remember { mutableStateOf(initialEndDate) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Date Range") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quick options
                DateRangeOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedRange = option },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedRange == option,
                            onClick = { selectedRange = option }
                        )
                        Text(option.label)
                    }
                }

                // Custom date range (shown when CUSTOM selected)
                if (selectedRange == DateRangeOption.CUSTOM) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(onClick = { showStartPicker = true }) {
                            Text(customStartDate?.formatAsDate() ?: "Start Date")
                        }
                        Text("to")
                        OutlinedButton(onClick = { showEndPicker = true }) {
                            Text(customEndDate?.formatAsDate() ?: "End Date")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val (start, end) = when (selectedRange) {
                    DateRangeOption.ALL_TIME -> null to null
                    DateRangeOption.LAST_7_DAYS -> (today() - 7.days) to null
                    DateRangeOption.LAST_30_DAYS -> (today() - 30.days) to null
                    DateRangeOption.LAST_90_DAYS -> (today() - 90.days) to null
                    DateRangeOption.THIS_YEAR -> startOfYear() to null
                    DateRangeOption.CUSTOM -> customStartDate to customEndDate
                }
                onDateRangeSelected(start, end)
            }) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    // Date picker dialogs
    if (showStartPicker) {
        DatePickerDialog(
            onDateSelected = { customStartDate = it; showStartPicker = false },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        DatePickerDialog(
            onDateSelected = { customEndDate = it; showEndPicker = false },
            onDismiss = { showEndPicker = false }
        )
    }
}

enum class DateRangeOption(val label: String) {
    ALL_TIME("All Time"),
    LAST_7_DAYS("Last 7 Days"),
    LAST_30_DAYS("Last 30 Days"),
    LAST_90_DAYS("Last 90 Days"),
    THIS_YEAR("This Year"),
    CUSTOM("Custom Range")
}
```

#### Step 3: Update AnalyticsScreen Export Dialog
**File**: `shared/src/commonMain/kotlin/com/devil/phoenixproject/presentation/screen/AnalyticsScreen.kt`

Replace the current export menu with enhanced version:
```kotlin
// State for date range
var showDateRangePicker by remember { mutableStateOf(false) }
var selectedStartDate by remember { mutableStateOf<Long?>(null) }
var selectedEndDate by remember { mutableStateOf<Long?>(null) }

// In export dialog, modify "Workout History" button:
OutlinedButton(
    onClick = { showDateRangePicker = true },
    enabled = !isExporting && allWorkoutSessions.isNotEmpty(),
    modifier = Modifier.fillMaxWidth()
) {
    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
    Spacer(modifier = Modifier.width(8.dp))
    Text("Workout History (${allWorkoutSessions.size})")
}

// Show date range picker when triggered
if (showDateRangePicker) {
    DateRangePickerDialog(
        onDateRangeSelected = { start, end ->
            selectedStartDate = start
            selectedEndDate = end
            showDateRangePicker = false

            // Perform export with date range
            isExporting = true
            scope.launch(Dispatchers.Default) {
                val result = csvExporter.exportWorkoutHistoryFiltered(
                    workoutSessions = allWorkoutSessions,
                    startDate = start,
                    endDate = end,
                    exerciseNames = exerciseNames.toMap(),
                    weightUnit = weightUnit,
                    formatWeight = viewModel::formatWeight
                )
                // ... handle result
            }
        },
        onDismiss = { showDateRangePicker = false }
    )
}
```

#### Step 4: Show Record Count Preview
Before export, show how many records will be exported:
```kotlin
// Calculate count for preview
val filteredCount = remember(allWorkoutSessions, selectedStartDate, selectedEndDate) {
    allWorkoutSessions.count { session ->
        val afterStart = selectedStartDate == null || session.timestamp >= selectedStartDate
        val beforeEnd = selectedEndDate == null || session.timestamp <= selectedEndDate
        afterStart && beforeEnd
    }
}

Text("$filteredCount workouts will be exported")
```

#### Step 5: Add Export Progress for Large Datasets
For large exports, show progress indicator:
```kotlin
// In CsvExporter implementations
fun exportWorkoutHistoryFiltered(
    // ... params ...
    onProgress: ((Int, Int) -> Unit)? = null  // current, total
): Result<String>
```

### Dependencies
- Material 3 DatePicker (already available in Compose Material 3)

### Effort Estimate
- Interface Updates: Low
- Date Range Picker: Medium
- UI Integration: Low
- Platform Implementations: Low
- **Total: Low-Medium**

---

## Implementation Priority & Phasing

### Phase 1: Quick Wins (Low Effort, High Impact)
1. **Export Date Range Selection** - Addresses immediate user frustration
2. **Skip/Go Back in Routine** - Core usability improvement

### Phase 2: Core Enhancements (Medium Effort)
3. **Adjust Weight During Routine** - Critical for real-world workout flexibility
4. **Custom Exercises** - Enables personalization
5. **Echo Mode Average Weight** - Completes feature parity with original app

### Phase 3: Advanced Features (High Effort)
6. **Superset Creation** - Advanced routine building capability
7. **Gamification System** - Engagement and retention feature

---

## Technical Considerations

### Database Migrations
- Features 1, 2, 4, 6 require database schema changes
- Use SQLDelight's migration system for production upgrades
- Test migrations thoroughly with existing user data

### BLE Protocol
- Feature 3 (weight adjustment) needs protocol verification
- Ensure machine accepts weight changes mid-workout

### Platform-Specific Code
- Date pickers need platform implementations (expect/actual)
- Export enhancements affect all three platform implementations

### Testing Strategy
- Unit tests for domain logic (badge calculations, phase detection)
- Integration tests for database migrations
- UI tests for new components
- Manual testing with actual Vitruvian hardware for BLE features

---

## Summary

| Feature | Effort | Priority | Dependencies |
|---------|--------|----------|--------------|
| 1. Custom Exercises | Medium | P2 | None |
| 2. Gamification | High | P3 | Streak tracking (exists) |
| 3. Weight Adjustment | Medium | P2 | BLE verification |
| 4. Supersets | High | P3 | None |
| 5. Skip/Go Back | Medium | P1 | None |
| 6. Echo Averages | Medium-High | P2 | Phase detection |
| 7. Export Date Range | Low-Medium | P1 | None |
