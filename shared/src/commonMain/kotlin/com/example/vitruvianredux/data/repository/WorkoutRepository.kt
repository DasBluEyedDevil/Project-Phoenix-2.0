package com.example.vitruvianredux.data.repository

import com.example.vitruvianredux.domain.model.PersonalRecord
import com.example.vitruvianredux.domain.model.Routine
import com.example.vitruvianredux.domain.model.WorkoutSession
import com.example.vitruvianredux.data.local.WeeklyProgramWithDays
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Personal record entity - stub for database
 */
data class PersonalRecordEntity(
    val id: Long = 0,
    val exerciseId: String,
    val weightPerCableKg: Float,
    val reps: Int,
    val timestamp: Long,
    val workoutMode: String
)

/**
 * Workout Repository interface
 * TODO: Implement with SQLDelight for actual database operations
 */
interface WorkoutRepository {
    // Workout sessions
    fun getAllSessions(): Flow<List<WorkoutSession>>
    suspend fun saveSession(session: WorkoutSession)
    suspend fun deleteSession(sessionId: String)
    suspend fun deleteAllSessions()

    // Routines
    fun getAllRoutines(): Flow<List<Routine>>
    suspend fun saveRoutine(routine: Routine)
    suspend fun updateRoutine(routine: Routine)
    suspend fun deleteRoutine(routineId: String)
    suspend fun getRoutineById(routineId: String): Routine?

    // Weekly programs
    fun getAllPrograms(): Flow<List<WeeklyProgramWithDays>>
    fun getActiveProgram(): Flow<WeeklyProgramWithDays?>
    suspend fun activateProgram(programId: String)
    suspend fun deleteProgram(programId: String)

    // Personal records
    fun getAllPersonalRecords(): Flow<List<PersonalRecordEntity>>
    suspend fun updatePRIfBetter(exerciseId: String, weightKg: Float, reps: Int, mode: String)
}

/**
 * Stub Workout Repository for compilation
 */
class StubWorkoutRepository : WorkoutRepository {
    override fun getAllSessions() = flowOf(emptyList<WorkoutSession>())
    override suspend fun saveSession(session: WorkoutSession) {}
    override suspend fun deleteSession(sessionId: String) {}
    override suspend fun deleteAllSessions() {}

    override fun getAllRoutines() = flowOf(emptyList<Routine>())
    override suspend fun saveRoutine(routine: Routine) {}
    override suspend fun updateRoutine(routine: Routine) {}
    override suspend fun deleteRoutine(routineId: String) {}
    override suspend fun getRoutineById(routineId: String): Routine? = null

    override fun getAllPrograms() = flowOf(emptyList<WeeklyProgramWithDays>())
    override fun getActiveProgram() = flowOf(null)
    override suspend fun activateProgram(programId: String) {}
    override suspend fun deleteProgram(programId: String) {}

    override fun getAllPersonalRecords() = flowOf(emptyList<PersonalRecordEntity>())
    override suspend fun updatePRIfBetter(exerciseId: String, weightKg: Float, reps: Int, mode: String) {}
}
