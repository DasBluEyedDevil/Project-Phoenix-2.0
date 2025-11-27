# ConnectionLogs Implementation Plan

## Overview

This document outlines the implementation plan to achieve parity for:
- `ConnectionLogsScreen.kt`: KMP (37 lines stub) → Parent (428 lines full)
- `ConnectionLogsViewModel.kt`: KMP (32 lines stub) → Parent (380 lines full)

**Total Gap:** ~739 lines of complex logging, filtering, and export functionality

---

## Phase 1: Data Layer - Connection Logging Infrastructure

### 1.1 Create ConnectionLogEntity (if not exists)

**Location:** `shared/src/commonMain/.../data/local/ConnectionLogEntity.kt`

```kotlin
data class ConnectionLogEntity(
    val id: Long = 0,
    val timestamp: Long,
    val level: LogLevel,
    val eventType: String,
    val message: String,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val details: String? = null,
    val metadata: String? = null
)

enum class LogLevel {
    DEBUG, INFO, WARNING, ERROR
}
```

**Estimated effort:** 15 minutes

---

### 1.2 Add SQLDelight Schema for Connection Logs

**Location:** `shared/src/commonMain/sqldelight/.../ConnectionLog.sq`

```sql
CREATE TABLE ConnectionLog (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp INTEGER NOT NULL,
    level TEXT NOT NULL,
    eventType TEXT NOT NULL,
    message TEXT NOT NULL,
    deviceName TEXT,
    deviceAddress TEXT,
    details TEXT,
    metadata TEXT
);

getAllLogs:
SELECT * FROM ConnectionLog ORDER BY timestamp DESC;

getLogsByLevel:
SELECT * FROM ConnectionLog WHERE level = ? ORDER BY timestamp DESC;

getLogsSince:
SELECT * FROM ConnectionLog WHERE timestamp > ? ORDER BY timestamp DESC;

insertLog:
INSERT INTO ConnectionLog (timestamp, level, eventType, message, deviceName, deviceAddress, details, metadata)
VALUES (?, ?, ?, ?, ?, ?, ?, ?);

deleteAllLogs:
DELETE FROM ConnectionLog;

deleteOldLogs:
DELETE FROM ConnectionLog WHERE timestamp < ?;

getLogCount:
SELECT COUNT(*) FROM ConnectionLog;

getErrorCount:
SELECT COUNT(*) FROM ConnectionLog WHERE level = 'ERROR';

getWarningCount:
SELECT COUNT(*) FROM ConnectionLog WHERE level = 'WARNING';
```

**Estimated effort:** 30 minutes

---

### 1.3 Create ConnectionLogger Interface

**Location:** `shared/src/commonMain/.../data/logger/ConnectionLogger.kt`

```kotlin
interface ConnectionLogger {
    fun log(
        level: LogLevel,
        eventType: String,
        message: String,
        deviceName: String? = null,
        deviceAddress: String? = null,
        details: String? = null,
        metadata: String? = null
    )

    fun debug(eventType: String, message: String, details: String? = null)
    fun info(eventType: String, message: String, details: String? = null)
    fun warning(eventType: String, message: String, details: String? = null)
    fun error(eventType: String, message: String, details: String? = null)

    suspend fun getAllLogs(): List<ConnectionLogEntity>
    suspend fun clearAllLogs()
    suspend fun deleteOldLogs(olderThanMillis: Long)
}

class ConnectionLoggerImpl(
    private val database: VitruvianDatabase
) : ConnectionLogger {
    override fun log(
        level: LogLevel,
        eventType: String,
        message: String,
        deviceName: String?,
        deviceAddress: String?,
        details: String?,
        metadata: String?
    ) {
        database.connectionLogQueries.insertLog(
            timestamp = currentTimeMillis(),
            level = level.name,
            eventType = eventType,
            message = message,
            deviceName = deviceName,
            deviceAddress = deviceAddress,
            details = details,
            metadata = metadata
        )
    }

    override fun debug(eventType: String, message: String, details: String?) =
        log(LogLevel.DEBUG, eventType, message, details = details)

    override fun info(eventType: String, message: String, details: String?) =
        log(LogLevel.INFO, eventType, message, details = details)

    override fun warning(eventType: String, message: String, details: String?) =
        log(LogLevel.WARNING, eventType, message, details = details)

    override fun error(eventType: String, message: String, details: String?) =
        log(LogLevel.ERROR, eventType, message, details = details)

    override suspend fun getAllLogs(): List<ConnectionLogEntity> =
        database.connectionLogQueries.getAllLogs().executeAsList().map { it.toEntity() }

    override suspend fun clearAllLogs() =
        database.connectionLogQueries.deleteAllLogs()

    override suspend fun deleteOldLogs(olderThanMillis: Long) =
        database.connectionLogQueries.deleteOldLogs(currentTimeMillis() - olderThanMillis)
}
```

**Estimated effort:** 1 hour

---

## Phase 2: ViewModel Implementation

### 2.1 Complete ConnectionLogsViewModel

**Location:** `shared/src/commonMain/.../presentation/viewmodel/ConnectionLogsViewModel.kt`

```kotlin
class ConnectionLogsViewModel(
    private val connectionLogger: ConnectionLogger,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    // Filter states
    private val _selectedLevelFilter = MutableStateFlow<LogLevel?>(null)
    val selectedLevelFilter: StateFlow<LogLevel?> = _selectedLevelFilter.asStateFlow()

    private val _selectedEventTypeFilter = MutableStateFlow<String?>(null)
    val selectedEventTypeFilter: StateFlow<String?> = _selectedEventTypeFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // All logs from database
    private val _allLogs = MutableStateFlow<List<ConnectionLogEntity>>(emptyList())

    // Filtered logs (reactive)
    val filteredLogs: StateFlow<List<ConnectionLogEntity>> = combine(
        _allLogs,
        _selectedLevelFilter,
        _selectedEventTypeFilter,
        _searchQuery
    ) { logs, levelFilter, eventTypeFilter, query ->
        logs.filter { log ->
            val matchesLevel = levelFilter == null || log.level == levelFilter
            val matchesEventType = eventTypeFilter == null || log.eventType == eventTypeFilter
            val matchesQuery = query.isBlank() || listOfNotNull(
                log.message,
                log.deviceName,
                log.eventType,
                log.details
            ).any { it.contains(query, ignoreCase = true) }

            matchesLevel && matchesEventType && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Log statistics
    val logStats: StateFlow<LogStats> = _allLogs.map { logs ->
        LogStats(
            total = logs.size,
            errors = logs.count { it.level == LogLevel.ERROR },
            warnings = logs.count { it.level == LogLevel.WARNING },
            info = logs.count { it.level == LogLevel.INFO },
            debug = logs.count { it.level == LogLevel.DEBUG }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LogStats())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadLogs()
    }

    private fun loadLogs() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _allLogs.value = connectionLogger.getAllLogs()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setLevelFilter(level: LogLevel?) {
        _selectedLevelFilter.value = level
    }

    fun setEventTypeFilter(eventType: String?) {
        _selectedEventTypeFilter.value = eventType
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            connectionLogger.clearAllLogs()
            _allLogs.value = emptyList()
        }
    }

    fun cleanupOldLogs(daysToKeep: Int = 7) {
        viewModelScope.launch {
            val cutoffMillis = daysToKeep * 24 * 60 * 60 * 1000L
            connectionLogger.deleteOldLogs(cutoffMillis)
            loadLogs() // Refresh
        }
    }

    suspend fun exportLogs(): String {
        val logs = _allLogs.value
        val builder = StringBuilder()

        // Header
        builder.appendLine("═══════════════════════════════════════════════════════")
        builder.appendLine("       VITRUVIAN CONNECTION DEBUG LOG EXPORT")
        builder.appendLine("═══════════════════════════════════════════════════════")
        builder.appendLine()

        // System info
        builder.appendLine("SYSTEM INFORMATION")
        builder.appendLine("─────────────────────────────────────────────────────")
        builder.appendLine("Export Time: ${formatTimestamp(currentTimeMillis())}")
        builder.appendLine("Total Logs: ${logs.size}")
        builder.appendLine()

        // Device info (from first log with device info)
        logs.firstOrNull { it.deviceName != null }?.let { log ->
            builder.appendLine("VITRUVIAN DEVICE")
            builder.appendLine("─────────────────────────────────────────────────────")
            builder.appendLine("Device: ${log.deviceName}")
            builder.appendLine("Address: ${log.deviceAddress}")
            builder.appendLine()
        }

        // Event log
        builder.appendLine("EVENT LOG")
        builder.appendLine("─────────────────────────────────────────────────────")
        logs.forEach { log ->
            val timestamp = formatTimestamp(log.timestamp, "HH:mm:ss.SSS")
            builder.appendLine("[$timestamp] [${log.level}] ${log.eventType}")
            log.deviceName?.let { builder.appendLine("  Device: $it (${log.deviceAddress})") }
            builder.appendLine("  ${log.message}")
            log.details?.let {
                builder.appendLine("  Details:")
                it.lines().forEach { line ->
                    builder.appendLine("    $line")
                }
            }
            builder.appendLine()
        }

        // Force data section (last 3 workouts)
        builder.appendLine()
        builder.appendLine("FORCE DATA (Last 3 Workouts)")
        builder.appendLine("─────────────────────────────────────────────────────")

        val recentSessions = workoutRepository.getRecentSessions(3).first()
        recentSessions.forEach { session ->
            val exerciseName = session.exerciseId?.let {
                exerciseRepository.getExerciseById(it)?.name
            } ?: "Unknown"

            builder.appendLine()
            builder.appendLine("Workout: $exerciseName")
            builder.appendLine("Time: ${formatTimestamp(session.timestamp)}")
            builder.appendLine("Mode: ${session.mode}")
            builder.appendLine("Reps: ${session.totalReps}")

            // Get metrics and analyze forces
            val metrics = workoutRepository.getMetricsForSession(session.id).first()
            if (metrics.isNotEmpty()) {
                val forceData = analyzeRepForces(metrics)
                forceData.forEachIndexed { index, rep ->
                    builder.appendLine("  Rep ${index + 1}:")
                    builder.appendLine("    Up A: ${rep.upForceA_min}-${rep.upForceA_max} (avg: ${rep.upForceA_avg})")
                    builder.appendLine("    Up B: ${rep.upForceB_min}-${rep.upForceB_max} (avg: ${rep.upForceB_avg})")
                    builder.appendLine("    Down A: ${rep.downForceA_min}-${rep.downForceA_max} (avg: ${rep.downForceA_avg})")
                    builder.appendLine("    Down B: ${rep.downForceB_min}-${rep.downForceB_max} (avg: ${rep.downForceB_avg})")
                }
            }
        }

        return builder.toString()
    }

    private fun analyzeRepForces(metrics: List<WorkoutMetric>): List<RepForceData> {
        if (metrics.size < 3) return emptyList()

        val repForces = mutableListOf<RepForceData>()
        var currentRep = mutableListOf<WorkoutMetric>()
        var isGoingUp = true

        for (i in 1 until metrics.size) {
            val prevPos = (metrics[i-1].positionA + metrics[i-1].positionB) / 2f
            val currPos = (metrics[i].positionA + metrics[i].positionB) / 2f

            val wasGoingUp = isGoingUp
            isGoingUp = currPos > prevPos

            currentRep.add(metrics[i])

            // Detect rep completion (direction change from down to up)
            if (!wasGoingUp && isGoingUp && currentRep.size > 10) {
                // Analyze completed rep
                val upPhase = currentRep.filter { /* ascending position */ true }.take(currentRep.size / 2)
                val downPhase = currentRep.drop(currentRep.size / 2)

                repForces.add(RepForceData(
                    upForceA_min = upPhase.minOfOrNull { it.loadA } ?: 0f,
                    upForceA_max = upPhase.maxOfOrNull { it.loadA } ?: 0f,
                    upForceA_avg = upPhase.map { it.loadA }.average().toFloat(),
                    upForceB_min = upPhase.minOfOrNull { it.loadB } ?: 0f,
                    upForceB_max = upPhase.maxOfOrNull { it.loadB } ?: 0f,
                    upForceB_avg = upPhase.map { it.loadB }.average().toFloat(),
                    downForceA_min = downPhase.minOfOrNull { it.loadA } ?: 0f,
                    downForceA_max = downPhase.maxOfOrNull { it.loadA } ?: 0f,
                    downForceA_avg = downPhase.map { it.loadA }.average().toFloat(),
                    downForceB_min = downPhase.minOfOrNull { it.loadB } ?: 0f,
                    downForceB_max = downPhase.maxOfOrNull { it.loadB } ?: 0f,
                    downForceB_avg = downPhase.map { it.loadB }.average().toFloat()
                ))

                currentRep = mutableListOf()
            }
        }

        return repForces
    }
}

data class LogStats(
    val total: Int = 0,
    val errors: Int = 0,
    val warnings: Int = 0,
    val info: Int = 0,
    val debug: Int = 0
)

private data class RepForceData(
    val upForceA_min: Float, val upForceA_max: Float, val upForceA_avg: Float,
    val upForceB_min: Float, val upForceB_max: Float, val upForceB_avg: Float,
    val downForceA_min: Float, val downForceA_max: Float, val downForceA_avg: Float,
    val downForceB_min: Float, val downForceB_max: Float, val downForceB_avg: Float
)
```

**Estimated effort:** 3 hours

---

## Phase 3: Screen Implementation

### 3.1 Complete ConnectionLogsScreen

**Location:** `shared/src/commonMain/.../presentation/screen/ConnectionLogsScreen.kt`

```kotlin
@Composable
fun ConnectionLogsScreen(
    viewModel: ConnectionLogsViewModel,
    mainViewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val logs by viewModel.filteredLogs.collectAsState()
    val stats by viewModel.logStats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val levelFilter by viewModel.selectedLevelFilter.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        TopAppBar(
            title = { Text("Connection Logs") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                IconButton(onClick = {
                    scope.launch {
                        exportText = viewModel.exportLogs()
                        showExportDialog = true
                    }
                }) {
                    Icon(Icons.Default.Share, "Export")
                }
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(Icons.Default.Delete, "Clear")
                }
            }
        )

        // Stats card
        LogStatsCard(stats = stats)

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search logs...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, "Clear")
                    }
                }
            },
            singleLine = true
        )

        // Filter chips
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = levelFilter == LogLevel.ERROR,
                onClick = {
                    viewModel.setLevelFilter(
                        if (levelFilter == LogLevel.ERROR) null else LogLevel.ERROR
                    )
                },
                label = { Text("Errors (${stats.errors})") },
                leadingIcon = { Icon(Icons.Default.Error, null, tint = Color.Red) }
            )
            FilterChip(
                selected = levelFilter == LogLevel.WARNING,
                onClick = {
                    viewModel.setLevelFilter(
                        if (levelFilter == LogLevel.WARNING) null else LogLevel.WARNING
                    )
                },
                label = { Text("Warnings (${stats.warnings})") },
                leadingIcon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFFFA500)) }
            )
            FilterChip(
                selected = levelFilter == LogLevel.INFO,
                onClick = {
                    viewModel.setLevelFilter(
                        if (levelFilter == LogLevel.INFO) null else LogLevel.INFO
                    )
                },
                label = { Text("Info (${stats.info})") },
                leadingIcon = { Icon(Icons.Default.Info, null, tint = Color.Blue) }
            )
        }

        // Logs list
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (logs.isEmpty()) {
            EmptyStateComponent(
                icon = Icons.Default.Assignment,
                title = "No logs found",
                subtitle = if (searchQuery.isNotEmpty() || levelFilter != null)
                    "Try adjusting your filters"
                else
                    "Connection events will appear here"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    LogEntryCard(log = log)
                }
            }
        }
    }

    // Clear confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Logs?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllLogs()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Logs") },
            text = {
                Column {
                    Text(
                        "Debug log exported. You can share this with developers to help diagnose issues.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Report issues at:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    SelectionContainer {
                        Text(
                            "github.com/project-phoenix/issues",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Share the export text
                        // Platform-specific sharing handled by expect/actual
                        showExportDialog = false
                    }
                ) {
                    Text("Share")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun LogStatsCard(stats: LogStats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(value = stats.total.toString(), label = "Total", color = MaterialTheme.colorScheme.primary)
            StatItem(value = stats.errors.toString(), label = "Errors", color = Color.Red)
            StatItem(value = stats.warnings.toString(), label = "Warnings", color = Color(0xFFFFA500))
            StatItem(value = stats.info.toString(), label = "Info", color = Color.Blue)
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LogEntryCard(log: ConnectionLogEntity) {
    val levelColor = when (log.level) {
        LogLevel.ERROR -> Color.Red
        LogLevel.WARNING -> Color(0xFFFFA500)
        LogLevel.INFO -> Color.Blue
        LogLevel.DEBUG -> Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, levelColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timestamp
                Text(
                    text = formatTimestamp(log.timestamp, "HH:mm:ss.SSS"),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Level badge
                Surface(
                    color = levelColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = log.level.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = levelColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Event type
            Text(
                text = log.eventType,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            // Message
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodyMedium
            )

            // Device info
            if (log.deviceName != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${log.deviceName} (${log.deviceAddress})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Details
            if (!log.details.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = log.details,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }
        }
    }
}
```

**Estimated effort:** 3 hours

---

## Phase 4: Integration

### 4.1 Add to Koin DI Module

```kotlin
// In AppModule.kt
single<ConnectionLogger> { ConnectionLoggerImpl(get()) }
viewModel { ConnectionLogsViewModel(get(), get(), get()) }
```

### 4.2 Integrate Logger into BLE Repository

Add logging calls throughout `VitruvianBleManager.kt`:

```kotlin
connectionLogger.info("BLE_SCAN", "Starting device scan")
connectionLogger.debug("BLE_CONNECT", "Connecting to ${device.name}", details = "Address: ${device.address}")
connectionLogger.error("BLE_ERROR", "Connection failed", details = exception.stackTraceToString())
```

**Estimated effort:** 2 hours

---

## Implementation Summary

| Phase | Description | Estimated Time |
|-------|-------------|----------------|
| 1 | Data Layer (Entity, SQLDelight, Logger) | 2 hours |
| 2 | ViewModel Implementation | 3 hours |
| 3 | Screen Implementation | 3 hours |
| 4 | Integration (DI, BLE logging) | 2 hours |
| **TOTAL** | | **~10 hours** |

---

## Files to Create/Modify

### New Files:
- `shared/src/commonMain/.../data/local/ConnectionLogEntity.kt`
- `shared/src/commonMain/sqldelight/.../ConnectionLog.sq`
- `shared/src/commonMain/.../data/logger/ConnectionLogger.kt`

### Modified Files:
- `shared/src/commonMain/.../presentation/viewmodel/ConnectionLogsViewModel.kt`
- `shared/src/commonMain/.../presentation/screen/ConnectionLogsScreen.kt`
- `shared/src/commonMain/.../di/AppModule.kt`
- `shared/src/androidMain/.../data/ble/VitruvianBleManager.kt`

---

*Document created: 2025-11-27*
