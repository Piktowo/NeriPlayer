package moe.ouom.neriplayer.util

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

fun convertTimestampToDate(timestamp: Long): String {
    val date = Date(timestamp)
    @SuppressLint("SimpleDateFormat")
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    return sdf.format(date)
}

fun formatTotalDuration(ms: Long): String {
    if (ms <= 0) return "0分钟"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return if (h > 0) "${h}小时${m}分钟" else "${m}分钟"
}

@SuppressLint("DefaultLocale")
fun formatDurationSec(seconds: Int): String {
    if (seconds <= 0) return "00:00"
    val total = seconds.toLong()
    val hours = TimeUnit.SECONDS.toHours(total)
    val minutes = TimeUnit.SECONDS.toMinutes(total) % 60
    val secs = (total % 60).toInt()
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, secs)
    else String.format("%02d:%02d", minutes, secs)
}