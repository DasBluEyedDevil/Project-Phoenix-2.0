package com.example.vitruvianredux.util

import kotlinx.datetime.*
import kotlin.math.roundToInt

/**
 * KMP-compatible utility functions to replace Java dependencies
 */
object KmpUtils {

    /**
     * Format a timestamp to a human-readable date string
     * @param timestamp Unix timestamp in milliseconds
     * @param pattern Date pattern (simplified KMP-compatible)
     * @return Formatted date string
     */
    fun formatTimestamp(timestamp: Long, pattern: String = "MMM dd, yyyy"): String {
        val instant = Instant.fromEpochMilliseconds(timestamp)
        val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

        return when (pattern) {
            "MMM dd, yyyy" -> {
                val month = dateTime.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                val day = dateTime.dayOfMonth.toString().padStart(2, '0')
                val year = dateTime.year
                "$month $day, $year"
            }
            "MM/dd/yyyy" -> {
                val month = dateTime.monthNumber.toString().padStart(2, '0')
                val day = dateTime.dayOfMonth.toString().padStart(2, '0')
                val year = dateTime.year
                "$month/$day/$year"
            }
            "yyyy-MM-dd" -> {
                val month = dateTime.monthNumber.toString().padStart(2, '0')
                val day = dateTime.dayOfMonth.toString().padStart(2, '0')
                val year = dateTime.year
                "$year-$month-$day"
            }
            "HH:mm" -> {
                val hour = dateTime.hour.toString().padStart(2, '0')
                val minute = dateTime.minute.toString().padStart(2, '0')
                "$hour:$minute"
            }
            "h:mm a" -> {
                val hour = if (dateTime.hour == 0) 12 else if (dateTime.hour > 12) dateTime.hour - 12 else dateTime.hour
                val minute = dateTime.minute.toString().padStart(2, '0')
                val amPm = if (dateTime.hour < 12) "AM" else "PM"
                "$hour:$minute $amPm"
            }
            "EEE" -> {
                dateTime.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            }
            "EEEE" -> {
                dateTime.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
            }
            else -> {
                // Default fallback
                val month = dateTime.monthNumber.toString().padStart(2, '0')
                val day = dateTime.dayOfMonth.toString().padStart(2, '0')
                val year = dateTime.year
                "$month/$day/$year"
            }
        }
    }

    /**
     * Format a relative timestamp (e.g., "2 hours ago", "Yesterday")
     * @param timestamp Unix timestamp in milliseconds
     * @return Relative time string
     */
    fun formatRelativeTimestamp(timestamp: Long): String {
        val now = Clock.System.now().toEpochMilliseconds()
        val diffMs = now - timestamp
        val diffMinutes = diffMs / (1000 * 60)
        val diffHours = diffMinutes / 60
        val diffDays = diffHours / 24

        return when {
            diffMinutes < 1 -> "Just now"
            diffMinutes < 60 -> "${diffMinutes}m ago"
            diffHours < 24 -> "${diffHours}h ago"
            diffDays == 1L -> "Yesterday"
            diffDays < 7 -> "${diffDays}d ago"
            else -> formatTimestamp(timestamp, "MMM dd, yyyy")
        }
    }

    /**
     * Get current time in milliseconds
     * @return Current Unix timestamp in milliseconds
     */
    fun currentTimeMillis(): Long {
        return Clock.System.now().toEpochMilliseconds()
    }

    /**
     * Get current day of week (ISO-8601: 1=Monday, 7=Sunday)
     * @return Day of week value (1-7)
     */
    fun currentDayOfWeek(): Int {
        val now = Clock.System.now()
        val localDate = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
        return localDate.dayOfWeek.isoDayNumber
    }

    /**
     * Format a duration in milliseconds to a human-readable string
     * @param millis Duration in milliseconds
     * @return Formatted duration string (e.g., "1h 23m 45s")
     */
    fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /**
     * Format a float with specified decimal places
     * @param value Float value to format
     * @param decimals Number of decimal places
     * @return Formatted string
     */
    fun formatFloat(value: Float, decimals: Int): String {
        if (decimals <= 0) {
            return value.roundToInt().toString()
        }

        var factor = 1f
        repeat(decimals) { factor *= 10f }

        val rounded = (value * factor).roundToInt() / factor
        val intPart = rounded.toLong()
        val decPart = ((rounded - intPart) * factor).roundToInt()

        return "$intPart.${"$decPart".padStart(decimals, '0')}"
    }

    /**
     * Format a double with specified decimal places
     * @param value Double value to format
     * @param decimals Number of decimal places
     * @return Formatted string
     */
    fun formatDouble(value: Double, decimals: Int): String {
        return formatFloat(value.toFloat(), decimals)
    }
}

/**
 * Extension function to format Float
 */
fun Float.format(decimals: Int): String = KmpUtils.formatFloat(this, decimals)

/**
 * Extension function to format Double
 */
fun Double.format(decimals: Int): String = KmpUtils.formatDouble(this, decimals)
