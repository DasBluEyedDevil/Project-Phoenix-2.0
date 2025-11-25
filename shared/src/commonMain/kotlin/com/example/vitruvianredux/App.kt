package com.example.vitruvianredux

import androidx.compose.runtime.*
import androidx.navigation.compose.rememberNavController
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.presentation.navigation.NavGraph
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.ui.theme.VitruvianTheme
import com.example.vitruvianredux.ui.theme.ThemeMode
import org.koin.compose.KoinContext
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject

@Composable
fun App() {
    KoinContext {
        val viewModel = koinViewModel<MainViewModel>()
        val exerciseRepository = koinInject<ExerciseRepository>()
        val navController = rememberNavController()
        
        // Theme state - temporarily local, ideally from preferences
        var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }

        VitruvianTheme(themeMode = themeMode) {
            NavGraph(
                navController = navController,
                viewModel = viewModel,
                exerciseRepository = exerciseRepository,
                themeMode = themeMode,
                onThemeModeChange = { themeMode = it }
            )
        }
    }
}