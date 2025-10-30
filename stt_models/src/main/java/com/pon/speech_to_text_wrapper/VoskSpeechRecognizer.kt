package com.pon.speech_to_text_wrapper

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

class VoskSpeechRecognizer private constructor() : RecognitionListener {
    var rec: Recognizer? = null
    private var settings: SettingsRepository? = null
    private var currentModel: Model? = null
    var processor: VoiceProcessor? = null
    private var speechService: SpeechService? = null
    private var currentString: String = ""
    var modelDirectory = ""
    var state = VoskState.CREATED_NOT_READY
        private set
    private val gson = Gson()
    private var callOnFinished: Runnable? = null
    var liveTextField = MutableLiveData("")
    private var callOnInitError: Runnable? = null

    fun prepare(
        appContext: Context,
        onVoskReady: Runnable,
        onInitError: Runnable,
        onFinished: Runnable
    ) {
        settings = SettingsRepository(appContext.applicationContext)
        callOnFinished = onFinished
        currentString = ""
        callOnInitError = onInitError
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        initModel(appContext, onVoskReady, onInitError)
    }

    private fun initModel(
        appContext: Context,
        onVoskReady: Runnable,
        onInitError: Runnable
    ) {
        StorageService.unpack(
            appContext, modelDirectory, "model",
            { model ->
                currentModel = model
                state = VoskState.INITIALISED_READY
                onVoskReady.run()
            }
        ) {
            state = VoskState.CREATED_NOT_READY
            onInitError.run()
        }
    }

    override fun onResult(hypothesis: String?) {
        if (hypothesis == null || hypothesis.trim().isEmpty()) return
        val sentenceResult = gson.fromJson(hypothesis, SentenceResult::class.java)
        val newWordsString = sentenceResult.text
        if (newWordsString.trim().isEmpty()) return
        currentString = liveTextField.value.toString()
        currentString = processInput(currentString, newWordsString)
        liveTextField.postValue(currentString)
    }

    override fun onFinalResult(hypothesis: String) {
        state = VoskState.FINISHED_AND_READY
        liveTextField.postValue(currentString)
        callOnFinished?.run()
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
        callOnInitError?.run()
        stop()
        release()
    }

    override fun onTimeout() {
        //showToast("Error, other application use microphone now");
        callOnInitError?.run()
        stop()
        release()
    }

    @Throws(IOException::class)
    fun recognizeMic() {
        if ((state == VoskState.INITIALISED_READY || state == VoskState.FINISHED_AND_READY) && currentModel != null) {
            try {
                val sampleRate = settings?.voskMicSampleRate ?: 16000f
                rec = Recognizer(currentModel, sampleRate)
                speechService = SpeechService(rec, sampleRate)
                speechService?.startListening(this)
                currentString = ""
                liveTextField.value = ""

                state = VoskState.WORKING_MIC
            } catch (e: Exception) {
                callOnInitError?.run()
            }
        } else callOnInitError?.run()
    }

    fun pause(on: Boolean) {
        if (state == VoskState.WORKING_MIC) {
            speechService?.setPause(on)
        }
    }

    fun stop() {
        if (state == VoskState.WORKING_MIC) speechService?.stop()
        rec?.close()
        state = VoskState.FINISHED_AND_READY
    }

    fun release() {
        if (state == VoskState.CREATED_NOT_READY || state == VoskState.FAILURE) return
        stop()
        speechService?.shutdown()
        currentModel?.close()
        currentModel = null
        speechService = null
        rec = null
        state = VoskState.CREATED_NOT_READY
    }

    val isReady: Boolean
        get() = state == VoskState.FINISHED_AND_READY || state == VoskState.INITIALISED_READY

    class WordResult {
        @SerializedName("conf")
        var conf = 0.0
        @SerializedName("end")
        var end = 0.0
        @SerializedName("start")
        var start = 0.0
        @SerializedName("word")
        var word: String = ""
    }

    private class SentenceResult {
        override fun toString(): String {
            val s = arrayOf("Sentence result")
            result.listIterator().forEachRemaining { x: WordResult ->
                s[0] = """${s[0]}${x.word} ${x.conf}"""
            }
            return s[0] + "Text =" + text
        }
        @SerializedName("result")
        var result: List<WordResult> = emptyList()
        @SerializedName("text")
        var text: String = ""
    }

    companion object {
        @get:Synchronized
        val instance: VoskSpeechRecognizer by lazy { VoskSpeechRecognizer() }
    }

    interface VoiceProcessor {
        fun processWords(newWords: String, currentString: String)
    }
}