package c.ponom.swenska.tts

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import net.gotev.speech.Speech
import net.gotev.speech.TextToSpeechCallback
import wrapper.internal.SettingsRepository
import java.util.Locale

/**
Объект Speaker для работы с синтезом речи (Text-To-Speech,TTS API).
 */
private const val TAG = "SpeakerApi"
@Suppress("unused")
object SpeakerApi {

    private val speaker: Speech by lazy { Speech.getInstance() }
    private var speakerAvailable = false

    /**
     * Возвращает статус готовности TTS API
     *    @return true при успешной инициализации.
     *    В случае если API еще не было инициализировано, обращения к методам speakPhrase(...),
     *    setVoice(...) и другим игнорируются без создания исключения
     *
     */
    fun isAvailable() = speakerAvailable

    private var settings: SettingsRepository? = null
    private var readyCallback: (result: Boolean) -> Unit = {}

    /**
     * Возвращает список голосов, поддерживаемых синтезатором речи.
     * TTS API должно быть успешно инициализировано до вызова метода,
     * @return список объектов Voice или пустой список в случае ошибки либо
     * неинициализированного API TTS .
     */
    fun getVoiceList(): List<Voice>  {
        if (!speakerAvailable) return emptyList()
        return try {
            speaker.supportedTextToSpeechVoices
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Возвращает текущий установленный голос, если синтезатор доступен, иначе null.
     * @return текущий голос или null.
     */
    fun currentVoice(): Voice? =
        if (speakerAvailable) speaker.textToSpeechVoice
        else null

    /**
     * Подготавливает синтезатор речи к работе.
     * Инициализирует синтезатор, если он ещё не доступен.
     * @param context контекст Android.
     * @param onReadyCallback вызывается с результатом готовности (true - готов, false - ошибка).
     * после успешной инициализации возможны обращения к методам  speakPhrase(...),
     * При неуспешной инициализации последующие обращения к этим и другим  методам
     * будут игнорироваться
     * */
    fun prepare(context: Context, onReadyCallback: (result: Boolean) -> Unit = {}) {
        if (!speakerAvailable) {
            Speech.init(context, ttsInitListener)
            settings = SettingsRepository(context.applicationContext as Application)
            readyCallback = onReadyCallback
        } else onReadyCallback.invoke(true)
    }

    /**
     * Произносит переданную фразу, если синтезатор доступен.
     * TTS API должно быть успешно инициализировано до вызова метода
     * @param phrase текст для произношения.
     */
    fun speakPhrase(phrase: String) {
        if (speakerAvailable) speaker.say(phrase)
    }

    /**
     * Произносит фразу с колбэками начала произношения, конца и ошибки.
     * Может использоваться, к примеру, для управления анимацией или для  временного отключения
     * распознавания речи, произносимой синтезатором
     * TTS API должно быть успешно инициализировано до вызова метода
     * @param phrase текст для произношения.
     * @param callbackOnStart вызывается при начале речи.
     * @param callbackOnEnd вызывается при завершении речи.
     * @param callbackOnError вызывается при ошибке.
     */
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
                        speaker.setTextToSpeechRate(settings?.getSpeed() ?: 1.0f)
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

    /**
     * Освобождает ресурсы синтезатора речи.
     * Выключает синтезатор и меняет флаг доступности.
     */
    fun release() {
        if (speakerAvailable) speaker.shutdown()
        speakerAvailable = false
    }

    /**
     * Устанавливает и сохраняет в настройках скорость речи. TTS API должно быть успешно инициализировано
     * до вызова метода
     *  @param speed Скорость речи. 1.0 — нормальная скорость речи, меньшие значения замедляют речь
     *             (минимум 0.5 — половина нормальной скорости), большие значения ускоряют речь
     *             (максимум 2.0 — в два раза быстрее нормальной скорости).
     *
     */
    fun setSpeed(speed: Float) {
        if (speakerAvailable) {
            val rate = speed.coerceIn(0.5f, 2f)
            settings?.setSpeed(rate)
            speaker.setTextToSpeechRate(rate)
        }
    }

    /**
     * Получает название языка голоса с отображением локали и имени.
     * @return строка с названием языка и именем голоса.
     */
    fun Voice.getLangName(): String {
        return this.locale.displayName + "\n" + name
    }


    fun Voice.compareTo(v2: Voice): Int {
        return this.toString().compareTo(v2.toString())
    }

    /**
     * Устанавливает голос для синтезатора и сохраняет его в настройках.
     * TTS API должно быть успешно инициализировано до вызова метода
     * @param voice голос для установки.
     */
    fun setVoice(voice: Voice) {
        if (!speakerAvailable) return
        settings?.saveDefaultVoice(voice.name)
        speaker.setVoice(voice)
    }
}