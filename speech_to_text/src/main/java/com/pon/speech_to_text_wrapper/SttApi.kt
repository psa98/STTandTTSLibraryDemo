package com.pon.speech_to_text_wrapper

import android.app.Application
import android.content.Context
import com.google.gson.annotations.SerializedName
import com.pon.speech_to_text_wrapper.internal.AudioManagerApi
import com.pon.speech_to_text_wrapper.internal.VoskSpeechRecognizer
import com.pon.speech_to_text_wrapper.internal.VoskSpeechRecognizer.state
import kotlinx.coroutines.*

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


internal const val MAX_WORDS_STORED = 1000

@Suppress("unused")
object SttApi {

    private var recognizer: RecognizerAPI = RecognizerAPI
    private var audioManager:AudioManagerApi? = null
    /**
     * Асинхронно возвращает объект распознавателя речи.
     * Инициализация происходит только если распознаватель еще не был инициализирован.
     *
     * @param context контекст Android приложения
     * @return Deferred с объектом RecognizerAPI
     *
     * пример использования:
     *
     *            CoroutineScope(Dispatchers.IO).launch {
     *              try {
     *                      //сохраняем объект API для использования
     *                     recognizerApi = SttApi.getRecognizerAsync(context).await()
     *                     mainScope.launch {
     *                         recognizerApi?.allWords?.collect {
     *                             // обработка поступающих слов
     *                         }
     *                     }
     *                 } catch (e: Exception) {
     *                     //обработка ошибки
     *                 }
     *             }
     *
     */
    fun getRecognizerAsync(context: Context): Deferred<RecognizerAPI> {
        val deferred = CompletableDeferred<RecognizerAPI>()
        audioManager = AudioManagerApi(context.applicationContext as Application)
        audioManager?.turnScoOn()
        audioManager?.startReceiver()
        CoroutineScope(Dispatchers.IO).launch {
            if (recognizer.sttInitialized) {
                deferred.complete(recognizer)
            } else
                recognizer.init(
                    context,
                    onReady = { recognizer ->
                        recognizer.sttInitialized = true
                        deferred.complete(recognizer)
                    },
                    onError = {
                        recognizer.sttInitialized = false
                        deferred.completeExceptionally(it)
                    })
        }
        return deferred
    }

    /**
     * Состояния API распознавания речи.
     */
    enum class ApiState {
        CREATED_NOT_READY,   // Создано, но не готово
        INITIALISED_READY,   // Инициализировано и готово
        WORKING_MIC,         // Распознает речь с микрофона
        FINISHED_AND_READY   // Завершило работу и готово к началу нового сеанса распознавания речи
    }

    object RecognizerAPI {
        internal var sttInitialized = false
        private val voskSpeechRecognizer: VoskSpeechRecognizer = VoskSpeechRecognizer
        /**
         * Флоу с последними распознанными словами. Слова поступают в поток после завершения предложения -
         * после паузы в речи).
         */
        val lastWords: StateFlow<String> = voskSpeechRecognizer.lastWords.asStateFlow()

        /**
         * Флоу с последними распознанными словами. Строка состоит из всех слов распознанных в текущем
         * сеансе, с момента инициализации API. Новые слова поступают в поток после завершения предложения
         * и присоединяются в конец строки. Длина строки ограничена тысячей последних распознанных слов,
         * константный параметр MAX_WORDS_STORED.
         * Может использоваться для разбора длинных распознанных предложений и текстов
         */
        val allWords: StateFlow<String> =
            voskSpeechRecognizer.allWords.asStateFlow()

        /**
         * Флоу с частичными результатами распознавания. Слова поступают в поток по мере распознавания,
         * до завершения приложения.
         */
        val partialWords: StateFlow<String> =
            voskSpeechRecognizer.partialResult.asStateFlow()

        /**
         * Флоу  с текущим состоянием API распознавания.
         *  CREATED_NOT_READY,   // Создано, но не готово
         *  INITIALISED_READY,   // Инициализировано и готово
         *  WORKING_MIC,         // Распознает речь с микрофона
         *  FINISHED_AND_READY   // Завершило работу и готово к началу нового сеанса распознавания речи
         */
        val apiState: StateFlow<ApiState> = voskSpeechRecognizer.apiState.asStateFlow()


        internal fun init(
            context: Context,
            onReady: (RecognizerAPI) -> Unit,
            onError: (Exception) -> Unit
        ) {
            voskSpeechRecognizer.prepare(
                appContext = context.applicationContext,
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

        /**
         * Запускает распознавание с микрофона.
         *
         * @param onError при указании лямбды, она вызывается при ошибке старта распознавания
         * @throws IllegalStateException если API не инициализировано
         */
        fun startMic(onError: (Exception) -> Unit = {}) {
            if (!sttInitialized) throw IllegalStateException("API не инициализировано")
            voskSpeechRecognizer.recognizeMic(onError)
        }
        /**
         * Останавливает распознавание речи с микрофона.
         *
         * @throws IllegalStateException если API не инициализировано
         */
        fun stopMic() {
            if (!sttInitialized) throw IllegalStateException("API не инициализировано")
            voskSpeechRecognizer.stop()
        }
        /**
         * Ставит распознавание с микрофона на паузу, если в данный момент идет распознавание с микрофона.
         *
         * @throws IllegalStateException если API не инициализировано
         */
        fun pauseMic() {
            if (!sttInitialized) throw IllegalStateException("API не инициализировано")
            voskSpeechRecognizer.pause(true)
        }
        /**
         * Снимает паузу с процессса распознавания с микрофона, если оно в данный момент временно
         * приостановлено вызовом pauseMic()
         *
         * @throws IllegalStateException если API не инициализировано
         */
        fun unpauseMic() {
            if (!sttInitialized) throw IllegalStateException("API не инициализировано")
            voskSpeechRecognizer.pause(false)
        }
        /**
         * Останавливает распознавание, освобождает ресурсы моделей распознавания речи.
         * Последущий вызов других методов API до его повторной инициализации через
         * getRecognizerAsync(...) будет вызывать исключение
         */
        fun releaseModels() {
            audioManager?.turnScoOff()
            audioManager?.stopReceiver()
            voskSpeechRecognizer.release()
            sttInitialized = false
        }

        /**
         * Проверка возможности вызова в данный момент метода startMic(...)
         */
        val sttReadyToStart: Boolean
            get() = state == ApiState.FINISHED_AND_READY || state == ApiState.INITIALISED_READY

        /**
         * Проверка, работает ли в данный  момент распознавание с микрофона.
         */
        val sttWorking: Boolean
            get() = state == ApiState.WORKING_MIC
    }

    internal class SentenceResult {
        @SerializedName("result")
        // более подробная информация о распознанных словах может быть включена в настройках Vosk
        // но пока не требуется
        var result: List<WordResult> = emptyList()
        @SerializedName("text")
        var text: String = ""
    }

    internal class WordResult {
        @SerializedName("conf")
        var conf = 0.0
        @SerializedName("end")
        var end = 0.0
        @SerializedName("start")
        var start = 0.0
        @SerializedName("word")
        var word: String = ""
    }

    internal class PartialResult {
        @SerializedName("partial")
        var partial = ""
    }
}