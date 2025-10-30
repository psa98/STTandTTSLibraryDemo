package com.pon.speech_to_text_wrapper

import android.app.Application
import android.content.Context
import com.google.gson.Gson
import com.pon.speech_to_text_wrapper.Api.ApiState
import com.pon.speech_to_text_wrapper.Api.SentenceResult
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
    var rec: Recognizer? = null
    private var settings: SettingsRepository? = null
    private var currentModel: Model? = null
    var processor: VoiceProcessor? = null
    private var speechService: SpeechService? = null
    private var currentString: String = ""
    var modelDirectory = ""
    var state = ApiState.CREATED_NOT_READY
        private set
    private val gson = Gson()
    var callOnInitError = {}
    internal val lastWords: MutableStateFlow<String> = MutableStateFlow("")
    internal val lastWordsResult: MutableStateFlow<SentenceResult?> = MutableStateFlow(null)
    internal val finalResultWords: MutableStateFlow<String> = MutableStateFlow("")
    internal val finalResult: MutableStateFlow<SentenceResult?> = MutableStateFlow(null)
    internal val apiState: MutableStateFlow<ApiState> = MutableStateFlow(ApiState.CREATED_NOT_READY)



    fun prepare(
        appContext: Context,
        onVoskReady: ()-> Unit,
        onInitError: (e:Exception) -> Unit,
    ) {
        settings = SettingsRepository(appContext.applicationContext as Application)
        currentString = ""
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
        val sentenceResult = gson.fromJson(hypothesis, SentenceResult::class.java)
        val newWordsString = sentenceResult.text
        if (newWordsString.trim().isEmpty()) return
        val currentString = lastWords.value.toString()+newWordsString
        lastWords.value = currentString
        lastWordsResult.value = sentenceResult
    }

    override fun onFinalResult(hypothesis: String?) {
        state = ApiState.FINISHED_AND_READY
        if (hypothesis == null || hypothesis.trim().isEmpty()) {
            finalResultWords.value = ""
            finalResult.value = null
            return
        }
        val sentenceResult = gson.fromJson(hypothesis, SentenceResult::class.java)
        val newWordsString = sentenceResult.text
        if (newWordsString.trim().isEmpty()) {
            finalResultWords.value = ""
            finalResult.value = null
            return
        }
        finalResultWords.value = newWordsString
        finalResult.value = sentenceResult
    }

    private fun processInput(previousString: String, response: String): String {
        // тут возможна иная обработка новых полученных слов, к примеру детекция ключевых слов
        // или анализ всей предшествующей строки
        val nextString = response.trim()
        processor?.processWords(response, previousString)
        return "$nextString \n$previousString"
    }

    override fun onPartialResult(hypothesis: String) {}
    override fun onError(e: Exception) {
        callOnInitError()
        stop()
        release()
    }

    override fun onTimeout() {
        //showToast("Error, other application use microphone now");
        callOnInitError()
        stop()
        release()
    }

    @Throws(IOException::class)
    fun recognizeMic(onInitError: (Exception) -> Unit = {}) {
        if ((state == ApiState.INITIALISED_READY || state == ApiState.FINISHED_AND_READY) && currentModel != null) {
            try {
                val sampleRate = settings?.voskMicSampleRate ?: 16000f
                rec = Recognizer(currentModel, sampleRate)
                speechService = SpeechService(rec, sampleRate)
                speechService?.startListening(this)
                currentString = ""
                finalResultWords.value = ""
                finalResult.value = null
                lastWords.value = ""
                lastWordsResult.value = null
                state = ApiState.WORKING_MIC
            } catch (e: Exception) {
                onInitError(e)
            }
        } else onInitError(IllegalStateException("Ошибка: api должно быть в состоянии INITIALISED_READY или" +
                " FINISHED_AND_READY. Библиотека не инициалиирована или ее ресурсы уже освобождены") )
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
        if (state == ApiState.CREATED_NOT_READY || state == ApiState.FAILURE) return
        stop()
        speechService?.shutdown()
        currentModel?.close()
        currentModel = null
        speechService = null
        rec = null
        state = ApiState.CREATED_NOT_READY
    }


    interface VoiceProcessor {
        fun processWords(newWords: String, currentString: String)
    }
}