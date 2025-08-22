package moe.ouom.neriplayer.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

object SettingsKeys {
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val FORCE_DARK = booleanPreferencesKey("force_dark")
    val FOLLOW_SYSTEM_DARK = booleanPreferencesKey("follow_system_dark")
    val DISCLAIMER_ACCEPTED_V2 = booleanPreferencesKey("disclaimer_accepted_v2")
    val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
    val BILI_AUDIO_QUALITY = stringPreferencesKey("bili_audio_quality")
    val KEY_DEV_MODE = booleanPreferencesKey("dev_mode_enabled")
    val THEME_SEED_COLOR = stringPreferencesKey("theme_seed_color")
    val LYRIC_BLUR_ENABLED = booleanPreferencesKey("lyric_blur_enabled")
    val UI_DENSITY_SCALE = floatPreferencesKey("ui_density_scale")
    val BYPASS_PROXY = booleanPreferencesKey("bypass_proxy")
    val BACKGROUND_IMAGE_URI = stringPreferencesKey("background_image_uri")
    val BACKGROUND_IMAGE_BLUR = floatPreferencesKey("background_image_blur")
    val BACKGROUND_IMAGE_ALPHA = floatPreferencesKey("background_image_alpha")
}

object ThemeDefaults {
    const val DEFAULT_SEED_COLOR_HEX = "0061A4"
    val PRESET_COLORS = listOf(
        "0061A4",
        "6750A4",
        "B3261E",
        "C425A8",
        "00897B",
        "388E3C",
        "FBC02D",
        "E65100",
    )
}
class SettingsRepository(private val context: Context) {
    val dynamicColorFlow: Flow<Boolean> =
        context.dataStore.data.map { it[SettingsKeys.DYNAMIC_COLOR] ?: true }

    val forceDarkFlow: Flow<Boolean> =
        context.dataStore.data.map { it[SettingsKeys.FORCE_DARK] ?: false }

    val followSystemDarkFlow: Flow<Boolean> =
        context.dataStore.data.map { it[SettingsKeys.FOLLOW_SYSTEM_DARK] ?: true }

    val audioQualityFlow: Flow<String> =
        context.dataStore.data.map { it[SettingsKeys.AUDIO_QUALITY] ?: "exhigh" }

    val biliAudioQualityFlow: Flow<String> =
        context.dataStore.data.map { it[SettingsKeys.BILI_AUDIO_QUALITY] ?: "high" }

    val devModeEnabledFlow: Flow<Boolean> =
        context.dataStore.data.map { it[SettingsKeys.KEY_DEV_MODE] ?: false }

    val themeSeedColorFlow: Flow<String> =
        context.dataStore.data.map { it[SettingsKeys.THEME_SEED_COLOR] ?: ThemeDefaults.DEFAULT_SEED_COLOR_HEX }

    val lyricBlurEnabledFlow: Flow<Boolean> =
        context.dataStore.data.map { it[SettingsKeys.LYRIC_BLUR_ENABLED] ?: true }

    val uiDensityScaleFlow: Flow<Float> =
        context.dataStore.data.map { it[SettingsKeys.UI_DENSITY_SCALE] ?: 1.0f }

    val bypassProxyFlow: Flow<Boolean> =
        context.dataStore.data.map { it[SettingsKeys.BYPASS_PROXY] ?: true }

    val backgroundImageUriFlow: Flow<String?> =
        context.dataStore.data.map { it[SettingsKeys.BACKGROUND_IMAGE_URI] }

    val backgroundImageBlurFlow: Flow<Float> =
        context.dataStore.data.map { it[SettingsKeys.BACKGROUND_IMAGE_BLUR] ?: 0f }

    val backgroundImageAlphaFlow: Flow<Float> =
        context.dataStore.data.map { it[SettingsKeys.BACKGROUND_IMAGE_ALPHA] ?: 0.3f }

    val disclaimerAcceptedFlow: Flow<Boolean?> =
        flow {
            emit(null)
            val realFlow: Flow<Boolean> =
                context.dataStore.data.map { prefs ->
                    prefs[SettingsKeys.DISCLAIMER_ACCEPTED_V2] ?: false
                }
            emitAll(realFlow)
        }
    suspend fun setDynamicColor(value: Boolean) {
        context.dataStore.edit { it[SettingsKeys.DYNAMIC_COLOR] = value }
    }

    suspend fun setForceDark(value: Boolean) {
        context.dataStore.edit { it[SettingsKeys.FORCE_DARK] = value }
    }

    suspend fun setDisclaimerAccepted(accepted: Boolean) {
        context.dataStore.edit { it[SettingsKeys.DISCLAIMER_ACCEPTED_V2] = accepted }
    }

    suspend fun setAudioQuality(value: String) {
        context.dataStore.edit { it[SettingsKeys.AUDIO_QUALITY] = value }
    }

    suspend fun setBiliAudioQuality(value: String) {
        context.dataStore.edit { it[SettingsKeys.BILI_AUDIO_QUALITY] = value }
    }

    suspend fun setDevModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SettingsKeys.KEY_DEV_MODE] = enabled }
    }

    suspend fun setThemeSeedColor(hex: String) {
        context.dataStore.edit { it[SettingsKeys.THEME_SEED_COLOR] = hex }
    }

    suspend fun setLyricBlurEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SettingsKeys.LYRIC_BLUR_ENABLED] = enabled }
    }

    suspend fun setUiDensityScale(scale: Float) {
        context.dataStore.edit { it[SettingsKeys.UI_DENSITY_SCALE] = scale }
    }

    suspend fun setBypassProxy(enabled: Boolean) {
        context.dataStore.edit { it[SettingsKeys.BYPASS_PROXY] = enabled }
    }

    suspend fun setBackgroundImageUri(uri: String?) {
        context.dataStore.edit {
            if (uri == null) {
                it.remove(SettingsKeys.BACKGROUND_IMAGE_URI)
            } else {
                it[SettingsKeys.BACKGROUND_IMAGE_URI] = uri
            }
        }
    }

    suspend fun setBackgroundImageBlur(blur: Float) {
        context.dataStore.edit { it[SettingsKeys.BACKGROUND_IMAGE_BLUR] = blur }
    }

    suspend fun setBackgroundImageAlpha(alpha: Float) {
        context.dataStore.edit { it[SettingsKeys.BACKGROUND_IMAGE_ALPHA] = alpha }
    }

    suspend fun isDisclaimerAcceptedFirst(): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[SettingsKeys.DISCLAIMER_ACCEPTED_V2] ?: false
    }
}
