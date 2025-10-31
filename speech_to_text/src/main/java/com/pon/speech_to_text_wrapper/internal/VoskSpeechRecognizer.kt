package com.pon.speech_to_text_wrapper.internal

import android.content.Context
import com.google.gson.Gson
import com.pon.speech_to_text_wrapper.MAX_WORDS_STORED
import com.pon.speech_to_text_wrapper.SttApi
import kotlinx.coroutines.flow.MutableStateFlow
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

internal object VoskSpeechRecognizer : RecognitionListener {
    private var rec: Recognizer? = null
    private var currentModel: Model? = null
    private var speechService: SpeechService? = null
    var state = SttApi.ApiState.CREATED_NOT_READY
        set(value) {
            field = value
            apiState.value = value
        }
    private val gson = Gson()
    private var callOnInitError: (Exception) -> Unit = {}
    internal val allWords: MutableStateFlow<String> = MutableStateFlow("")
    internal val lastWords: MutableStateFlow<String> = MutableStateFlow("")
    internal val partialResult: MutableStateFlow<String> = MutableStateFlow("")
    internal val apiState: MutableStateFlow<SttApi.ApiState> =
        MutableStateFlow(SttApi.ApiState.CREATED_NOT_READY)
    private var seanceString = ""
        set(value) {
            val shortenedString =
                value.split(" ").toList().takeLast(MAX_WORDS_STORED).joinToString(" ")
             field = shortenedString
            allWords.value = shortenedString
        }

    internal fun prepare(
        appContext: Context,
        onVoskReady: () -> Unit,
        onInitError: (e: Exception) -> Unit,
    ) {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        StorageService.unpack(
            appContext, "model-ru-ru", "model",
            { model ->
                currentModel = model
                state = SttApi.ApiState.INITIALISED_READY
                onVoskReady()
            }
        ) { exception ->
            state = SttApi.ApiState.CREATED_NOT_READY
            onInitError(exception)
        }
    }


    override fun onResult(hypothesis: String?) {
        if (hypothesis == null || hypothesis.trim().isEmpty()) return
        val newWordsString = gson.fromJson(hypothesis, SttApi.SentenceResult::class.java).text
        if (newWordsString.trim().isEmpty()) return
        lastWords.value = newWordsString
        seanceString = "$seanceString $newWordsString"
    }

    override fun onFinalResult(hypothesis: String?) {
        state = SttApi.ApiState.FINISHED_AND_READY
        if (hypothesis == null || hypothesis.trim().isEmpty()) {
            lastWords.value = ""
            return
        }
        val sentenceResult = gson.fromJson(hypothesis, SttApi.SentenceResult::class.java)
        val newWordsString = sentenceResult.text
        if (newWordsString.trim().isEmpty()) {
            lastWords.value = ""
             return
        }
        lastWords.value = newWordsString
        seanceString = "$seanceString $newWordsString"
    }


    override fun onPartialResult(hypothesis: String) {
        val result = gson.fromJson(hypothesis, SttApi.PartialResult::class.java).partial
        if (result.isEmpty()) return
        partialResult.value = result
    }

    override fun onError(e: Exception) {
        callOnInitError(e)
        stop()
    }

    override fun onTimeout() {
        callOnInitError(IOException("Не удалось получить доступ к микрофону, таймаут"))
        stop()
    }

    @Throws(IOException::class)
    fun recognizeMic(onInitError: (Exception) -> Unit = {}) {
        if ((state == SttApi.ApiState.INITIALISED_READY || state == SttApi.ApiState.FINISHED_AND_READY) && currentModel != null) {
            try {
                callOnInitError = onInitError
                val sampleRate = 16000f
                rec = Recognizer(currentModel, sampleRate)
                speechService = SpeechService(rec, sampleRate)
                speechService?.startListening(this)
                partialResult.value = ""
                lastWords.value = ""
                state = SttApi.ApiState.WORKING_MIC
            } catch (e: Exception) {
                e.printStackTrace()
                onInitError(e)
            }
        } else onInitError(
            IllegalStateException(
                "Ошибка: api должно быть в состоянии INITIALISED_READY или" +
                        " FINISHED_AND_READY. Библиотека не инициализирована или ее ресурсы уже освобождены"
            )
        )
    }

    fun pause(on: Boolean) {
        if (state == SttApi.ApiState.WORKING_MIC) {
            speechService?.setPause(on)
        }
    }

    fun stop() {
        if (state == SttApi.ApiState.WORKING_MIC) speechService?.stop()
        rec?.close()
        state = SttApi.ApiState.FINISHED_AND_READY
    }

    fun release() {
        if (state == SttApi.ApiState.CREATED_NOT_READY) return
        stop()
        seanceString = ""
        speechService?.shutdown()
        currentModel?.close()
        currentModel = null
        speechService = null
        rec = null
        state = SttApi.ApiState.CREATED_NOT_READY
    }


}