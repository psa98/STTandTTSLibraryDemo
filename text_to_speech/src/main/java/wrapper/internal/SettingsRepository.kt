package wrapper.internal

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.Locale

@Suppress("unused")
class SettingsRepository(val context: Application) {


    fun containsKey(key: String): Boolean {
        return sharedPreferences.contains(key)
    }

    val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "default_tts_settings",
        Context.MODE_PRIVATE
    )


    fun saveDefaultVoice(name: String, locale: Locale = Locale.getDefault()) {
        sharedPreferences.edit { putString("VOICE_${locale.language}", name) }
    }


    fun getSavedVoice(locale: Locale = Locale.getDefault()): String {
        return sharedPreferences.getString("VOICE_${locale.language}", "") ?: ""
    }

    fun setSpeed(speed: Float) {
        sharedPreferences.edit { putFloat("SPEED", speed) }
    }

    fun getSpeed(): Float {
        return sharedPreferences.getFloat("SPEED", 1.0f)
    }


}