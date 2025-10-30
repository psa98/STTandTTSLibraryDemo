package wrapper

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import android.provider.Settings.Global.putFloat


@Suppress("unused")
class SettingsRepository(val context: Application) {


    fun containsKey(key: String): Boolean {
        return sharedPreferences.contains(key)
    }

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "default_tts_settings",
        Context.MODE_PRIVATE
    )

    var newVoiceSelectedPhrase = sharedPreferences.getString("VOICE_CHANGED_STRING","Голос изменен")!!
        set(value) {
            field = value
            sharedPreferences.edit { putString("SAMPLE_RATE", value) }
        }


    fun saveDefaultVoice(name: String) {
        sharedPreferences.edit { putString("VOICE", name) }
    }


    fun getSavedVoice(): String {
        return sharedPreferences.getString("VOICE", "") ?: ""
    }

}