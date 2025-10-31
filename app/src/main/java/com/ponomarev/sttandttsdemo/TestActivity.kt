package com.ponomarev.sttandttsdemo


import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import android.speech.tts.Voice
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import c.ponom.swenska.tts.SpeakerApi
import c.ponom.swenska.tts.SpeakerApi.compareTo
import com.pon.speech_to_text_wrapper.SttApi
import com.pon.speech_to_text_wrapper.SttApi.ApiState.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.collections.get

class TestActivity() : ComponentActivity() {

    private var recognizerApi: SttApi.RecognizerAPI? = null
    private var speakApi: SpeakerApi = SpeakerApi
    private val mainScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MainScreen() }
    }

    @Composable
    private fun MainScreen() {
        val context = LocalContext.current
        var apiState by remember { mutableStateOf(CREATED_NOT_READY) }
        var recordButtonEnabled by remember { mutableStateOf(false) }
        var recordButtonText by remember { mutableStateOf("Подготовка оборудования") }
        var permissionsButtonEnabled by remember { mutableStateOf(false) }
        var permissionsButtonText by remember { mutableStateOf("") }
        var currentText by remember { mutableStateOf("") }
        var partialText by remember { mutableStateOf("") }
        var textState by remember { mutableStateOf("Состояние API") }
        var textToSpeak by remember { mutableStateOf("Введите сюда текст для произношения") }
        var sayWordButtonText by remember { mutableStateOf("Подготовка оборудования") }
        var sayWordButtonEnabled by remember { mutableStateOf(false) }
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                recordButtonEnabled = true
                recordButtonText = "Начать распознавание"
            } else {
                recordButtonEnabled = false
                recordButtonText = "Разрешение для микрофона не получено"
            }
        }
        LaunchedEffect(Unit) {
            CoroutineScope(Dispatchers.IO).launch {
                /*
                возможный вариант без try
                val result = runCatching { SttApi.getRecognizerAsync(context).await() }
                result.onFailure {
                    it.printStackTrace()
                    recordButtonText = "Не удалось инициализировать API"
                    recordButtonEnabled = false
                }
                result.onSuccess {
                    recognizerApi= it
                    recordButtonEnabled = true
                    recordButtonText = "Начать распознавание"
                    mainScope.launch {
                        it.allWords.collect {
                            currentText = it.toString()
                        }
                    }
                }
                 */
                try {
                    val recognizer = SttApi.getRecognizerAsync(context).await()
                    recognizerApi = recognizer
                    recordButtonEnabled = true
                    recordButtonText = "Начать распознавание"
                    mainScope.launch {
                        recognizer.allWords.collect {
                            currentText = it.toString()
                        }
                    }
                    mainScope.launch {
                        recognizer.partialWords.collect {
                            partialText = it.toString()
                        }
                    }
                    mainScope.launch {
                        recognizer.apiState.collect {
                            textState = it.toString()
                            apiState = it
                            when (apiState) {
                                CREATED_NOT_READY -> {
                                    recordButtonText = "Идет подготовка оборудования"
                                    recordButtonEnabled = false
                                }

                                INITIALISED_READY -> {
                                    recordButtonText = "Начать распознавание"
                                    recordButtonEnabled = true

                                }

                                WORKING_MIC -> {
                                    recordButtonText = "Остановить запись"
                                    recordButtonEnabled = true
                                }
                                FINISHED_AND_READY -> {
                                    recordButtonText = "Начать распознавание"
                                    recordButtonEnabled = true
                                }
                            }
                        }
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    recordButtonText = "Не удалось инициализировать API"
                    recordButtonEnabled = false
                }
            }

            speakApi.prepare(context) { success ->
                if (success) {
                    sayWordButtonEnabled = true
                    textToSpeak = "Проверка голоса"
                    sayWordButtonText = "Произнести фразу"
                } else {
                    sayWordButtonEnabled = false
                    textToSpeak = "Проверка голоса"
                    sayWordButtonText = "Ошибка API TTS"
                }
            }
            if (!hasPermissions(context)) {
                permissionsButtonEnabled = true
                permissionsButtonText = "Нажмите для получения разрешения"
                recordButtonEnabled = false
                recordButtonText = "Разрешение для микрофона не получено"
            } else {
                permissionsButtonEnabled = false
                permissionsButtonText = "Разрешение на доступ к микрофону предоставлено"
                recordButtonEnabled = false
                recordButtonText = "Подготовка оборудования"
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        )
        {
            Button(
                onClick = {
                    launcher.launch(android.Manifest.permission.RECORD_AUDIO)
                },
                enabled = permissionsButtonEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(permissionsButtonText)
            }
            TextField(
                value = "Все распознанные слова:\n$currentText",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            )

            TextField(
                value = "Слова по мере распознавания:\n$partialText",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            )

            Text(text = textState)

            Button(
                onClick = {
                    val rec = recognizerApi
                    if (rec == null) return@Button
                    if (rec.apiState.value == WORKING_MIC) {
                        rec.stopMic()
                        return@Button
                    }
                    if (rec.sttReadyToStart == true) {
                        rec.startMic()
                    }
                },
                enabled = recordButtonEnabled,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(recordButtonText)
            }

            TextField(
                value = textToSpeak,
                onValueChange = { textToSpeak = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                singleLine = false,
                readOnly = false,
                placeholder = { Text(text = "Введите текст для произнесения") }
            )
            Button(
                onClick = {
                    speakApi.speakPhrase(
                        textToSpeak,
                        callbackOnStart = { recognizerApi?.pauseMic() },
                        callbackOnError = {},
                        callbackOnEnd = { recognizerApi?.unpauseMic() })
                },
                enabled = sayWordButtonEnabled,
                modifier = Modifier
                    .wrapContentWidth()
                    .align(Alignment.CenterHorizontally)
            ) {
                Text(text = sayWordButtonText)
            }

            Button(
                onClick = {
                    setTextToSpeechVoice()
                },
                enabled = sayWordButtonEnabled,
                modifier = Modifier
                    .wrapContentWidth()
                    .align(Alignment.CenterHorizontally)
            ) {
                Text(text = "Выбор голоса")
            }
        }
    }

    private fun hasPermissions(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PERMISSION_GRANTED
    }

    fun setTextToSpeechVoice() {
        // получаем список голосов русской локали
        var supportedVoices = SpeakerApi.getVoiceList()
            .sortedWith { v1: Voice, v2: Voice -> v1.compareTo(v2) }
            .filter {
                it.locale.language.startsWith("ru", true)
            }
        // нас интересуют голоса не требующие сети
        val offlineVoices = supportedVoices.filter { !it.isNetworkConnectionRequired }
        val currentVoice = SpeakerApi.currentVoice()
        if (supportedVoices.isEmpty() || currentVoice == null) {
            AlertDialog.Builder(this)
                .setTitle("Выбор голоса")
                .setMessage("Нет установленных голосов для русского языка")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val currentVoiceName = currentVoice.name
        // нас интересуют голоса не требующие сети (но если других русских нет и такие пойдут)
        if (offlineVoices.isNotEmpty()) supportedVoices = offlineVoices
        val voicesArray = arrayOfNulls<CharSequence>(supportedVoices.size)
        supportedVoices.forEachIndexed { index: Int, v: Voice ->
            voicesArray[index] = "Голос ${index+1} ${v.name} "
        }
        AlertDialog.Builder(this)
            .setTitle("Текущий голос: $currentVoiceName")
            .setItems(voicesArray) { _, i ->
                SpeakerApi.setVoice(supportedVoices[i])
                SpeakerApi.speakPhrase("Выбран новый голос")
            }
            .setPositiveButton("Отмена", null)
            .create()
            .show()
    }

    /**
     * Освобождаем все ресурсы и много памяти при уничтожении активити
     * Повторное подключение к Api распознавания потребует нового вызова
     * SttApi.getRecognizerAsync(...)
     */
    override fun onDestroy() {
        super.onDestroy()
        runCatching {
            recognizerApi?.stopMic()
            recognizerApi?.releaseModels()
            recognizerApi = null
        }
        speakApi.release()
    }

    /**
     * при переключении из приложения останавливаем распознавание - все равно у приложения не во
     * фронте доступ к микрофону блокируется, если нет форграунд сервиса
     */
    override fun onPause() {
        super.onPause()
        val rec = recognizerApi
        if (rec == null) return
        if (rec.apiState.value == WORKING_MIC) {
            rec.stopMic()
        }
    }


}