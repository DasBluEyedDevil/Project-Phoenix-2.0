package com.example.vitruvianredux.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.vitruvianredux.domain.model.ProgramMode
import com.example.vitruvianredux.domain.model.WeightUnit
import com.example.vitruvianredux.domain.model.WorkoutType
import com.example.vitruvianredux.presentation.components.CompactNumberPicker
import com.example.vitruvianredux.presentation.navigation.NavigationRoutes
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.ui.theme.ThemeMode
import co.touchlab.kermit.Logger

/**
 * Just Lift screen - quick workout configuration.
 * Allows selecting weight and mode, then starting immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JustLiftScreen(
    navController: NavController,
    viewModel: MainViewModel,
    themeMode: ThemeMode
) {
    // Set title
    LaunchedEffect(Unit) {
        viewModel.updateTopBarTitle("Just Lift")
    }

    val isAutoConnecting by viewModel.isAutoConnecting.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val params by viewModel.workoutParameters.collectAsState()
    val weightUnit by viewModel.weightUnit.collectAsState()

    // Determine actual theme
    val useDarkColors = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val backgroundGradient = if (useDarkColors) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0F172A),
                Color(0xFF1E1B4B),
                Color(0xFF172554)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFE0E7FF),
                Color(0xFFFCE7F3),
                Color(0xFFDDD6FE)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 100.dp) // Space for FAB/Button
        ) {
            item {
                Text(
                    text = "Just Lift",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Quick workout - pick weight and go!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Weight Selection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CompactNumberPicker(
                            value = viewModel.kgToDisplay(params.weightPerCableKg, weightUnit),
                            onValueChange = { displayVal ->
                                val kgVal = viewModel.displayToKg(displayVal, weightUnit)
                                viewModel.updateWorkoutParameters(params.copy(weightPerCableKg = kgVal))
                            },
                            range = 0f..100f,
                            step = 0.5f,
                            label = "Weight per Cable",
                            suffix = if (weightUnit == WeightUnit.KG) "kg" else "lb"
                        )
                    }
                }
            }

            // Mode Selection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Workout Mode",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        val modes = listOf(
                            ProgramMode.OldSchool,
                            ProgramMode.Pump,
                            ProgramMode.TUT,
                            ProgramMode.EccentricOnly
                        )
                        
                        val currentMode = (params.workoutType as? WorkoutType.Program)?.mode ?: ProgramMode.OldSchool

                        // Simple chip-like row for modes (wrapping if needed)
                        // Using Column for simplicity in V1
                        modes.forEach { mode ->
                            val selected = mode == currentMode
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateWorkoutParameters(
                                        params.copy(workoutType = WorkoutType.Program(mode))
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = if (selected) 
                                    ButtonDefaults.outlinedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                else ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text(mode.displayName)
                            }
                        }
                    }
                }
            }
            
            // Start Button Area
            item {
                Button(
                    onClick = {
                        Logger.d { "Starting Just Lift workout" }
                        viewModel.ensureConnection(
                            onConnected = {
                                viewModel.startWorkout(isJustLiftMode = true)
                                navController.navigate(NavigationRoutes.ActiveWorkout.route)
                            },
                            onFailed = { /* Error shown via StateFlow */ }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("START WORKOUT", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Auto-connect UI overlays
        if (isAutoConnecting) {
            com.example.vitruvianredux.presentation.components.ConnectingOverlay(
                onCancel = { viewModel.cancelAutoConnecting() }
            )
        }

        connectionError?.let { error ->
            com.example.vitruvianredux.presentation.components.ConnectionErrorDialog(
                message = error,
                onDismiss = { viewModel.clearConnectionError() }
            )
        }
    }
}