package c.ponom.swenska.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import net.gotev.speech.Speech
import net.gotev.speech.TextToSpeechCallback
import wrapper.SettingsRepository
import java.util.Locale


private const val TAG = "Android TTS"

/**

Объект Speaker предоставляет функционал для озвучивания текста с помощью TextToSpeech.
Управляет инициализацией, выбором голоса, воспроизведением фраз и освобождением ресурсов.

*/
object Speaker {

    private val speaker: Speech by lazy { Speech.getInstance() }

    private var speakerAvailable = false
    val settings: SettingsRepository? = null
    private var readyCallback: (result: Boolean) -> Unit = {}

    fun isAvailable(): Boolean =  speakerAvailable

    fun getVoiceList(): List<Voice> = try {
        speaker.supportedTextToSpeechVoices
    } catch (_: Exception) {
        emptyList()
    }

    fun currentVoice(): Voice? =
        if (speakerAvailable) speaker.textToSpeechVoice
        else null

    fun prepare(context: Context, onReadyCallback: (result: Boolean) -> Unit = {}) {
        if (!speakerAvailable) {
            Speech.init(context, ttsInitListener)
            readyCallback = onReadyCallback
        } else onReadyCallback.invoke(true)
    }

    fun speakPhrase(phrase: String) {
        if (speakerAvailable) speaker.say(phrase)
    }

    fun speakPhrase(
        phrase: String,
        callbackOnStart: () -> Unit,
        callbackOnEnd: () -> Unit,
        callbackOnError: () -> Unit
    ) {
        val callback: TextToSpeechCallback = object : TextToSpeechCallback {
            override fun onStart() {
                callbackOnStart.invoke()
            }

            override fun onCompleted() {
                callbackOnEnd.invoke()
            }

            override fun onError() {
                callbackOnError.invoke()
            }

        }
        if (speakerAvailable) speaker.say(phrase, callback)
    }

    private val ttsInitListener =
        TextToSpeech.OnInitListener { status ->
            when (status) {
                TextToSpeech.SUCCESS -> {
                    try {
                        @Suppress("DEPRECATION")
                        speaker.setLocale(Locale("ru", "RU"))
                        Log.i(TAG, "TextToSpeech engine successfully started")
                        val currentVoice = speaker
                            .supportedTextToSpeechVoices
                            ?.find { it.name == settings?.getSavedVoice() }
                        currentVoice?.let { speaker.setVoice(it) }

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    speakerAvailable = true
                    readyCallback(true)
                }

                TextToSpeech.ERROR -> {
                    Log.e(TAG, "Error while initializing TextToSpeech engine!")
                    readyCallback(false)
                }

                else -> {
                    Log.e(TAG, "Error while initializing TextToSpeech engine!")
                    readyCallback(false)
                }
            }
        }

    fun release() {
        speaker.shutdown()
        speakerAvailable = false
    }

    fun Voice.getLangName(): String {
        return this.locale.displayName + "\n" + name
    }

    fun Voice.compareTo(v2: Voice): Int {
        return this.toString().compareTo(v2.toString())
    }

    fun setVoice(voice: Voice) {
        settings?.saveDefaultVoice(voice.name)
        if (speakerAvailable) speaker.setVoice(voice)
    }

}