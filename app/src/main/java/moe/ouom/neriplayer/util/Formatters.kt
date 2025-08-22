package moe.ouom.neriplayer.util

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@SuppressLint("DefaultLocale")
fun formatPlayCount(count: Long): String {
    return when {
        count >= 100_000_000L -> String.format("%.1f亿", count / 100_000_000f)
        count >= 10_000L      -> String.format("%.1f万", count / 10_000f)
        else                  -> count.toString()
    }
}

@SuppressLint("DefaultLocale")
fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) -
            TimeUnit.MINUTES.toSeconds(minutes)
    return String.format("%02d:%02d", minutes, seconds)
}

@SuppressLint("DefaultLocale")
fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024L -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return formatter.format(date)
}