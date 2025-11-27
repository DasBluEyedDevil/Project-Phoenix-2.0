# AnalyticsScreen Implementation Plan

## Overview

This document outlines the implementation plan to achieve parity between the KMP `AnalyticsScreen.kt` (573 lines, ~24KB) and the parent Android project's `AnalyticsScreen.kt` (486 lines, ~55KB).

**Note:** The KMP version has more lines but is actually *simpler* - the parent's size comes from more complex components.

---

## Phase 1: Platform-Specific CSV Export

### 1.1 Create CsvExporter expect/actual

**Files to create:**

```
shared/src/commonMain/.../util/CsvExporter.kt (expect)
shared/src/androidMain/.../util/CsvExporter.android.kt (actual)
shared/src/desktopMain/.../util/CsvExporter.desktop.kt (actual)
shared/src/iosMain/.../util/CsvExporter.ios.kt (actual)
```

**Common interface:**

```kotlin
expect object CsvExporter {
    suspend fun exportPersonalRecords(
        records: List<PersonalRecord>,
        exerciseNames: Map<String, String>
    ): Result<String>  // Returns file path

    suspend fun exportWorkoutHistory(
        sessions: List<WorkoutSession>
    ): Result<String>

    suspend fun exportPRProgression(
        records: List<PersonalRecord>,
        exerciseNames: Map<String, String>
    ): Result<String>

    fun shareFile(filePath: String)
}
```

**Android implementation:**
- Use `Context.getExternalFilesDir()` or `MediaStore`
- Use `Intent.ACTION_SEND` for sharing

**Desktop implementation:**
- Use `java.io.File` with user's Documents folder
- Use `Desktop.getDesktop().open()` for opening

**iOS implementation:**
- Use `FileManager` and Documents directory
- Use `UIActivityViewController` for sharing

**Estimated effort:** 3 hours

---

### 1.2 Implement Export Dialog Options

Replace the placeholder dialog with actual export options:

```kotlin
if (showExportMenu) {
    AlertDialog(
        title = { Text("Export Data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportOptionButton(
                    icon = Icons.Default.Star,
                    label = "Export Personal Records",
                    onClick = {
                        scope.launch {
                            val result = CsvExporter.exportPersonalRecords(
                                personalRecords,
                                exerciseNames
                            )
                            result.onSuccess { path ->
                                CsvExporter.shareFile(path)
                            }
                        }
                    }
                )
                ExportOptionButton(
                    icon = Icons.Default.History,
                    label = "Export Workout History",
                    onClick = { /* ... */ }
                )
                ExportOptionButton(
                    icon = Icons.Default.TrendingUp,
                    label = "Export PR Progression",
                    onClick = { /* ... */ }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { showExportMenu = false }) {
                Text("Cancel")
            }
        }
    )
}
```

**Estimated effort:** 1 hour

---

## Phase 2: PR Progression Charts

### 2.1 Create WeightProgressionChart Component

**Location:** `shared/src/commonMain/.../presentation/components/charts/WeightProgressionChart.kt`

```kotlin
@Composable
fun WeightProgressionChart(
    dataPoints: List<PRDataPoint>,
    weightUnit: WeightUnit,
    modifier: Modifier = Modifier
) {
    // Canvas-based line chart showing weight progression over time
    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        // Draw axes
        // Plot data points
        // Connect with lines
        // Show weight labels
    }
}

data class PRDataPoint(
    val timestamp: Long,
    val weightPerCableKg: Float,
    val reps: Int
)
```

**Estimated effort:** 2 hours

---

### 2.2 Refactor ProgressionTab

Replace simple list with grouped progression view:

```kotlin
@Composable
fun ProgressionTab(
    personalRecords: List<PersonalRecord>,
    exerciseRepository: ExerciseRepository,
    weightUnit: WeightUnit
) {
    // Group PRs by exercise
    val groupedPRs = personalRecords.groupBy { it.exerciseId }

    LazyColumn {
        groupedPRs.forEach { (exerciseId, prs) ->
            item {
                ExerciseProgressionCard(
                    exerciseId = exerciseId,
                    prs = prs.sortedBy { it.timestamp },
                    exerciseRepository = exerciseRepository,
                    weightUnit = weightUnit
                )
            }
        }
    }
}

@Composable
fun ExerciseProgressionCard(
    exerciseId: String,
    prs: List<PersonalRecord>,
    exerciseRepository: ExerciseRepository,
    weightUnit: WeightUnit
) {
    var showChart by remember { mutableStateOf(true) }
    var exerciseName by remember { mutableStateOf("Loading...") }

    LaunchedEffect(exerciseId) {
        exerciseName = exerciseRepository.getExerciseById(exerciseId)?.name ?: "Unknown"
    }

    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with exercise name and toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(exerciseName, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { showChart = !showChart }) {
                    Icon(
                        if (showChart) Icons.Default.List else Icons.Default.ShowChart,
                        contentDescription = "Toggle view"
                    )
                }
            }

            // Stats summary
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatChip("Best", "${formatWeight(prs.maxOf { it.weightPerCableKg }, weightUnit)}")
                StatChip("PRs", "${prs.size}")
                StatChip("Latest", formatTimestamp(prs.last().timestamp))
            }

            // Chart or list
            if (showChart) {
                WeightProgressionChart(
                    dataPoints = prs.map { PRDataPoint(it.timestamp, it.weightPerCableKg, it.reps) },
                    weightUnit = weightUnit
                )
            } else {
                prs.forEach { pr ->
                    PRListItem(pr, weightUnit)
                }
            }
        }
    }
}
```

**Estimated effort:** 2 hours

---

## Phase 3: Calendar Heatmap

### 3.1 Create WorkoutCalendarHeatmap Component

**Location:** `shared/src/commonMain/.../presentation/components/WorkoutCalendarHeatmap.kt`

```kotlin
@Composable
fun WorkoutCalendarHeatmap(
    sessions: List<WorkoutSession>,
    weeksToShow: Int = 12,
    modifier: Modifier = Modifier
) {
    val today = remember { getCurrentDate() }
    val startDate = remember { today.minusWeeks(weeksToShow) }

    // Build workout count map
    val workoutCounts = remember(sessions) {
        sessions
            .groupBy { getDateFromTimestamp(it.timestamp) }
            .mapValues { it.value.size }
    }

    Column(modifier = modifier) {
        // Day labels (S M T W T F S)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                Text(
                    day,
                    modifier = Modifier.width(16.dp),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Calendar grid
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (week in 0 until weeksToShow) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (day in 0..6) {
                        val date = startDate.plusDays((week * 7 + day).toLong())
                        val count = workoutCounts[date] ?: 0
                        CalendarDay(count = count, isToday = date == today)
                    }
                }
            }
        }

        // Legend
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Less", style = MaterialTheme.typography.labelSmall)
            (0..4).forEach { level ->
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            getHeatmapColor(level),
                            RoundedCornerShape(2.dp)
                        )
                )
            }
            Text("More", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CalendarDay(count: Int, isToday: Boolean) {
    val level = when {
        count == 0 -> 0
        count == 1 -> 1
        count == 2 -> 2
        count <= 4 -> 3
        else -> 4
    }

    Box(
        modifier = Modifier
            .size(14.dp)
            .background(
                getHeatmapColor(level),
                RoundedCornerShape(2.dp)
            )
            .then(
                if (isToday) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                else Modifier
            )
    )
}

@Composable
private fun getHeatmapColor(level: Int): Color {
    return when (level) {
        0 -> MaterialTheme.colorScheme.surfaceVariant
        1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.primary
    }
}
```

**Estimated effort:** 2 hours

---

## Phase 4: Enhanced PR Cards

### 4.1 Create PersonalRecordCard Component

```kotlin
@Composable
fun PersonalRecordCard(
    rank: Int,
    pr: PersonalRecord,
    exerciseName: String,
    weightUnit: WeightUnit,
    modifier: Modifier = Modifier
) {
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (rank <= 3)
                rankColor.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(rankColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "#$rank",
                    style = MaterialTheme.typography.titleMedium,
                    color = rankColor
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // PR details
            Column(modifier = Modifier.weight(1f)) {
                Text(exerciseName, style = MaterialTheme.typography.titleMedium)
                Text(
                    formatTimestamp(pr.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Weight and reps
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatWeight(pr.weightPerCableKg, weightUnit),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${pr.reps} reps",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
```

**Estimated effort:** 1 hour

---

## Phase 5: Thread Safety Fixes

### 5.1 Add Dispatchers.IO for Database Calls

Fix potential threading issues in DashboardTab:

```kotlin
// Before (problematic):
val exercise = try {
    exerciseRepository.getExerciseById(exerciseId)
} catch (e: Exception) {
    null
}

// After (thread-safe):
val exercise = withContext(Dispatchers.IO) {
    try {
        exerciseRepository.getExerciseById(exerciseId)
    } catch (e: Exception) {
        null
    }
}
```

Apply this pattern throughout the file where database/repository calls are made.

**Estimated effort:** 30 minutes

---

## Implementation Summary

| Phase | Description | Estimated Time |
|-------|-------------|----------------|
| 1 | CSV Export (expect/actual) | 4 hours |
| 2 | PR Progression Charts | 4 hours |
| 3 | Calendar Heatmap | 2 hours |
| 4 | Enhanced PR Cards | 1 hour |
| 5 | Thread Safety Fixes | 30 min |
| **TOTAL** | | **~11.5 hours** |

---

## Files to Create/Modify

### New Files:
- `shared/src/commonMain/.../util/CsvExporter.kt`
- `shared/src/androidMain/.../util/CsvExporter.android.kt`
- `shared/src/desktopMain/.../util/CsvExporter.desktop.kt`
- `shared/src/iosMain/.../util/CsvExporter.ios.kt`
- `shared/src/commonMain/.../presentation/components/charts/WeightProgressionChart.kt`
- `shared/src/commonMain/.../presentation/components/WorkoutCalendarHeatmap.kt`
- `shared/src/commonMain/.../presentation/components/PersonalRecordCard.kt`

### Modified Files:
- `shared/src/commonMain/.../presentation/screen/AnalyticsScreen.kt`

---

*Document created: 2025-11-27*
