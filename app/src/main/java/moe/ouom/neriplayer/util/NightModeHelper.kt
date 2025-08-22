package moe.ouom.neriplayer.util

import androidx.appcompat.app.AppCompatDelegate

object NightModeHelper {

    fun applyNightMode(
        followSystemDark: Boolean,
        forceDark: Boolean
    ) {
        val mode = when {
            forceDark -> AppCompatDelegate.MODE_NIGHT_YES
            followSystemDark -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}