package moe.ouom.neriplayer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.toColorInt
import com.materialkolor.rememberDynamicColorScheme

private val NeriTypography = Typography()

@Composable
fun NeriTheme(
    followSystemDark: Boolean,
    forceDark: Boolean,
    dynamicColor: Boolean,
    seedColorHex: String,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val isDark = when {
        forceDark -> true
        followSystemDark -> isSystemInDarkTheme()
        else -> false
    }

    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            val seed = Color(("#$seedColorHex").toColorInt())
            rememberDynamicColorScheme(seedColor = seed, isDark = isDark)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NeriTypography,
        content = content
    )
}