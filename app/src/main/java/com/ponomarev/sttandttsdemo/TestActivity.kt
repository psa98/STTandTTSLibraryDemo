package com.ponomarev.sttandttsdemo


import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHostState
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
import c.ponom.swenska.tts.Speaker
import com.pon.speech_to_text_wrapper.Api
import com.pon.speech_to_text_wrapper.Api.ApiState.WORKING_MIC
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TestActivity() : ComponentActivity() {

    private var recognizerApi: Api.Recognizer? = null
    var speakApi: Speaker = Speaker
    private val mainScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MainScreen() }
    }

    @Composable
    private fun MainScreen() {
        val context = LocalContext.current
        val snackbarHostState = SnackbarHostState()
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
                mainScope.launch {
                    snackbarHostState.showSnackbar("Требуется предоставить разрешение")
                }
            }
        }

        LaunchedEffect(Unit) {
            CoroutineScope(Dispatchers.IO).launch {
                Api().initApi(
                    context = context,
                    onReady = { recognizer ->
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
                            }
                        }
                    },
                    onError = { exception ->
                        recordButtonText = "Не удалось инициализировать API"
                        recordButtonEnabled = false
                    }
                )
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
        ) {
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
                        recordButtonText = "Начать распознавание"
                        return@Button
                    }
                    if (rec.sttReadyToStart == true) {
                        rec.startMic()
                        recordButtonText = "Остановить запись"
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
                enabled = true,
                readOnly = false,
                placeholder = null
            )

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    speakApi.speakPhrase(
                        textToSpeak,
                        callbackOnStart = { recognizerApi?.pauseMic() },
                        callbackOnError = {},
                        callbackOnEnd = { recognizerApi?.unpauseMic() })
                },
                enabled = sayWordButtonEnabled,
                modifier = Modifier.wrapContentWidth().align(Alignment.CenterHorizontally)

            ) {
                Text(text = sayWordButtonText)
            }
        }
    }

    private fun hasPermissions(context: android.content.Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PERMISSION_GRANTED
    }


    override fun onStop() {
        super.onStop()
        recognizerApi?.stopMic()
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizerApi?.stopMic()
        recognizerApi?.releaseModels()
    }

    override fun onPause() {
        super.onPause()
        recognizerApi?.stopMic()
    }


}