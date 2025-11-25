package com.example.vitruvianredux.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Compact Number Picker - Compose-based number picker
 * Provides reliable number selection with buttons
 * Supports both integer and fractional values with configurable step size
 *
 * TODO: The original Android version used native Android NumberPicker (AndroidView).
 * This KMP version uses a simple button-based implementation instead.
 * For better UX on Android, consider using expect/actual to provide platform-specific pickers.
 */
@Composable
fun CompactNumberPicker(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    label: String = "",
    suffix: String = "",
    step: Float = 1.0f
) {
    // Generate array of values based on step
    val values = remember(range, step) {
        buildList {
            var current = range.start
            while (current <= range.endInclusive) {
                add(current)
                current += step
            }
        }
    }

    // Find current index
    val currentIndex = remember(value, values) {
        values.indexOfFirst { kotlin.math.abs(it - value) < 0.001f }.coerceAtLeast(0)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Row with -/+ buttons and value display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decrease button
            IconButton(
                onClick = {
                    val newIndex = (currentIndex - 1).coerceIn(values.indices)
                    onValueChange(values[newIndex])
                },
                enabled = currentIndex > 0,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease $label",
                    tint = if (currentIndex > 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            // Value display (TODO: Replace with wheel picker on Android via expect/actual)
            val displayValue = remember(currentIndex, values, step, suffix) {
                val v = values[currentIndex]
                val formatted = if (step >= 1.0f && v % 1.0f == 0f) {
                    v.toInt().toString()
                } else {
                    "%.1f".format(v)
                }
                if (suffix.isNotEmpty()) "$formatted $suffix" else formatted
            }

            Text(
                text = displayValue,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            )

            // Increase button
            IconButton(
                onClick = {
                    val newIndex = (currentIndex + 1).coerceIn(values.indices)
                    onValueChange(values[newIndex])
                },
                enabled = currentIndex < values.size - 1,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase $label",
                    tint = if (currentIndex < values.size - 1)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }
}

/**
 * Overload for backward compatibility with Int values
 */
@Composable
fun CompactNumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    label: String = "",
    suffix: String = ""
) {
    CompactNumberPicker(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.roundToInt()) },
        range = range.first.toFloat()..range.last.toFloat(),
        modifier = modifier,
        label = label,
        suffix = suffix,
        step = 1.0f
    )
}
