package com.pon.speech_to_text_wrapper

import android.content.Context
import com.google.gson.annotations.SerializedName
import com.pon.speech_to_text_wrapper.VoskSpeechRecognizer.state
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Api (context: Context) {

    private val appContext: Context = context.applicationContext
    private var recognizer: Recognizer? = null

    fun initApi(
        onReady: (recognizer: Recognizer) -> Unit,
        onError: (e: Exception) -> Unit
    ) {

        var api = recognizer
        if (api != null && recognizer?.sttInitialized==true) api else {
            recognizer = Recognizer(appContext)
            recognizer?.init(onReady, onError)
        }

    }

    enum class ApiState {
        CREATED_NOT_READY, INITIALISED_READY, WORKING_MIC, FINISHED_AND_READY, FAILURE
    }

    class Recognizer internal constructor(val context: Context) {
        var sttInitialized = false
        private val voskSpeechRecognizer: VoskSpeechRecognizer = VoskSpeechRecognizer
        val lastWords: StateFlow<String> = voskSpeechRecognizer.lastWords.asStateFlow()
        val lastWordsResult: StateFlow<SentenceResult?> =
            voskSpeechRecognizer.lastWordsResult.asStateFlow()
        val finalResultWords: StateFlow<String> =
            voskSpeechRecognizer.finalResultWords.asStateFlow()
        val finalResult: StateFlow<SentenceResult?> = voskSpeechRecognizer.finalResult.asStateFlow()
        val apiState: StateFlow<ApiState> = voskSpeechRecognizer.apiState.asStateFlow()

        fun init(onReady: (Recognizer) -> Unit, onError: (Exception) -> Unit) {
            voskSpeechRecognizer.prepare(
                appContext = context,
                onVoskReady = {
                    onReady.invoke(this)
                    sttInitialized = true
                },
                onInitError = { e: Exception ->
                    e.printStackTrace()
                    onError.invoke(e)
                    sttInitialized = false
                },
            )
        }

        fun startMic(onError: (Exception) -> Unit = {}) {
            voskSpeechRecognizer.recognizeMic(onError)
        }

        fun stopMic() {
            voskSpeechRecognizer.stop()
        }

        fun pauseMic() {
            voskSpeechRecognizer.pause(true)
        }

        fun unpauseMic() {
            voskSpeechRecognizer.pause(false)
        }

        fun releaseModels() {
            voskSpeechRecognizer.release()
            sttInitialized = false
        }

        val sttReadyToStart: Boolean
            get() = state == ApiState.FINISHED_AND_READY || state == ApiState.INITIALISED_READY

        val sttWorking: Boolean
            get() = state == ApiState.WORKING_MIC
    }

    class SentenceResult {
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


}