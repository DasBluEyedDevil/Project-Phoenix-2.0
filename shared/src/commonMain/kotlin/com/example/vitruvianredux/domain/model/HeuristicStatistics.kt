package com.example.vitruvianredux.domain.model

data class HeuristicStatistics(
    val concentric: HeuristicPhaseStatistics,
    val eccentric: HeuristicPhaseStatistics,
    val timestamp: Long = currentTimeMillis()
)
