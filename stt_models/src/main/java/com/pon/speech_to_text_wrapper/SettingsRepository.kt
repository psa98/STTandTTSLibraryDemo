package com.pon.speech_to_text_wrapper

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

@Suppress("unused")
class SettingsRepository(context: Application) {


    fun containsKey(key: String?): Boolean {
        return sharedPreferences.contains(key)
    }

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "default_stt_settings",
        Context.MODE_PRIVATE
    )

    var voskMicSampleRate = sharedPreferences.getFloat("SAMPLE_RATE", 16000f)
        set(value) {
            val v = value.coerceIn(8000f,44000f)
            field = v
            sharedPreferences.edit { putFloat("SAMPLE_RATE",v)  }
        }





}