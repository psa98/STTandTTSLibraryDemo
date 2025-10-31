package com.pon.speech_to_text_wrapper

import android.content.Context
import com.google.gson.Gson
import com.pon.speech_to_text_wrapper.SttApi.ApiState
import com.pon.speech_to_text_wrapper.SttApi.SentenceResult
import kotlinx.coroutines.flow.MutableStateFlow
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

private const val MAX_WORDS_STORED = 1000

internal object VoskSpeechRecognizer : RecognitionListener {
    var rec: Recognizer? = null
    private var currentModel: Model? = null
    private var speechService: SpeechService? = null
    var state = ApiState.CREATED_NOT_READY
        set(value) {
            field = value
            apiState.value = value
        }
    private val gson = Gson()
    var callOnInitError: (Exception) -> Unit = {}

    internal val allWords: MutableStateFlow<String> = MutableStateFlow("")
    internal val lastWords: MutableStateFlow<String> = MutableStateFlow("")
    internal val partialResult: MutableStateFlow<String> = MutableStateFlow("")
    internal val apiState: MutableStateFlow<ApiState> = MutableStateFlow(ApiState.CREATED_NOT_READY)
    var seanceString = ""
        set(value) {
            val shortenedString =
                value.split(" ").toList().takeLast(MAX_WORDS_STORED).joinToString(" ")
             field = shortenedString
            allWords.value = shortenedString
        }

    fun prepare(
        appContext: Context,
        onVoskReady: () -> Unit,
        onInitError: (e: Exception) -> Unit,
    ) {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        StorageService.unpack(
            appContext, "model-ru-ru", "model",
            { model ->
                currentModel = model
                state = ApiState.INITIALISED_READY

                onVoskReady()
            }
        ) { exception ->
            state = ApiState.CREATED_NOT_READY
            onInitError(exception)

        }
    }


    override fun onResult(hypothesis: String?) {
        if (hypothesis == null || hypothesis.trim().isEmpty()) return
        val newWordsString = gson.fromJson(hypothesis, SentenceResult::class.java).text
        if (newWordsString.trim().isEmpty()) return
        lastWords.value = newWordsString
        seanceString = "$seanceString $newWordsString"

    }

    override fun onFinalResult(hypothesis: String?) {
        state = ApiState.FINISHED_AND_READY
        if (hypothesis == null || hypothesis.trim().isEmpty()) {
            lastWords.value = ""
            return
        }
        val sentenceResult = gson.fromJson(hypothesis, SentenceResult::class.java)
        val newWordsString = sentenceResult.text
        if (newWordsString.trim().isEmpty()) {
            lastWords.value = ""
             return
        }
        lastWords.value = newWordsString
    }


    override fun onPartialResult(hypothesis: String) {
        val result = gson.fromJson(hypothesis, SttApi.PartialResult::class.java).partial
        if (result.isEmpty()) return
        partialResult.value = result
    }

    override fun onError(e: Exception) {
        callOnInitError(e)
        stop()
        release()
    }

    override fun onTimeout() {
        callOnInitError(IOException("Не удалось получить доступ к микрофону, таймаут"))
        stop()
        release()
    }

    @Throws(IOException::class)
    fun recognizeMic(onInitError: (Exception) -> Unit = {}) {

        if ((state == ApiState.INITIALISED_READY || state == ApiState.FINISHED_AND_READY) && currentModel != null) {
            try {
                callOnInitError = onInitError
                val sampleRate = 16000f
                rec = Recognizer(currentModel, sampleRate)
                speechService = SpeechService(rec, sampleRate)
                speechService?.startListening(this)
                partialResult.value = ""
                lastWords.value = ""
                state = ApiState.WORKING_MIC
            } catch (e: Exception) {
                onInitError(e)
            }
        } else onInitError(
            IllegalStateException(
                "Ошибка: api должно быть в состоянии INITIALISED_READY или" +
                        " FINISHED_AND_READY. Библиотека не инициалиирована или ее ресурсы уже освобождены"
            )
        )
    }

    fun pause(on: Boolean) {
        if (state == ApiState.WORKING_MIC) {
            speechService?.setPause(on)
        }
    }

    fun stop() {
        if (state == ApiState.WORKING_MIC) speechService?.stop()
        rec?.close()
        state = ApiState.FINISHED_AND_READY
    }

    fun release() {
        if (state == ApiState.CREATED_NOT_READY ) return
        stop()
        speechService?.shutdown()
        currentModel?.close()
        currentModel = null
        speechService = null
        rec = null
        state = ApiState.CREATED_NOT_READY
    }


}