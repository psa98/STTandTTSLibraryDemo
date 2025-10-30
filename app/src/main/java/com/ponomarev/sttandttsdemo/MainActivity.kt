package com.ponomarev.sttandttsdemo

import android.Manifest.permission.RECORD_AUDIO
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.pon.speech_to_text_wrapper.Api
import com.pon.speech_to_text_wrapper.Api.ApiState.WORKING_MIC
import com.ponomarev.sttandttsdemo.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val PERMISSION_REQUEST_CODE = 42
private const val TAG = "Main activity"

class MainActivity : AppCompatActivity() {
    val sttApi: Api by lazy { Api(this) }
    var recognizerApi: Api.Recognizer? = null
    val scope = CoroutineScope(Dispatchers.Main)
    private val binding: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setupViews()
        setupListeners()
        startSTTApi()
    }

    private fun startSTTApi() {
        sttApi.initApi(
            onReady = { recognizer ->
                recognizerApi = recognizer
                binding.recordButton.isEnabled = true
                binding.recordButton.text = "Начать распознавание"
                scope.launch {
                    recognizer.allWords.collect {
                        binding.partialText.setText(it.toString())
                    }
                }
                scope.launch {
                    recognizer.lastWords.collect {
                        binding.currentText.setText(it.toString())
                    }
                }
                scope.launch {
                    recognizer.apiState.collect {
                        binding.textState.text = it.toString()
                    }
                }


            },
            onError = {
                binding.recordButton.text = "Не удалось инициализировать API"
                binding.recordButton.isEnabled = false

            }
        )

    }



    private fun setupListeners() {
        binding.permissionsButton.setOnClickListener {
            requestPermissions(
                arrayOf(RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        }
        binding.recordButton.setOnClickListener {
            val rec = recognizerApi
            if (rec == null) return@setOnClickListener
            if (rec.apiState.value == WORKING_MIC) {
                rec.stopMic()
                binding.recordButton.text = "Начать распознавание"
                return@setOnClickListener
            }
            if (rec.sttReadyToStart == true) {
                rec.startMic()
                binding.recordButton.text = "Остановить запись"
            }

        }

    }

    override fun onStop() {
        super.onStop()
        if (recognizerApi!=null) {
            binding.recordButton.text = "Начать распознавание"

        }
        recognizerApi?.stopMic()
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizerApi?.stopMic()
        recognizerApi?.releaseModels()
    }


    private fun setupViews() {
        binding.recordButton.isEnabled = hasPermissions()
        if (!hasPermissions()) {
            binding.permissionsButton.text = "Нажмите для получения разрешения"
            binding.permissionsButton.isEnabled = true
            binding.recordButton.text = "Разрешение для микрофона не получено"
            binding.recordButton.isEnabled = false
        } else {
            binding.recordButton.text = "Подготовка оборудования"
            binding.recordButton.isEnabled = false
            binding.permissionsButton.text = "Разрешение на доступ к микрофону предоставлено"
            binding.permissionsButton.isEnabled = false
        }

    }


    private fun hasPermissions(): Boolean {
        return checkSelfPermission(RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Snackbar.make(
                binding.root, "Требуется предоставить разрешение",
                BaseTransientBottomBar.LENGTH_LONG
            ).show()
            binding.recordButton.text = "Разрешение для микрофона не получено"
            binding.recordButton.isEnabled = false
        } else {
            binding.recordButton.isEnabled = true
            binding.recordButton.text = "Начать pаспознавание"
        }
    }


    override fun onResume() {
        super.onResume()
        if (!hasPermissions()) {
            binding.permissionsButton.text = "Нажмите для получения разрешения"
            binding.permissionsButton.isEnabled = true
            binding.recordButton.text = "Разрешение для микрофона не получено"
            binding.recordButton.isEnabled = false
            return
        }
        val rec = recognizerApi
        if (rec == null) return
        if (rec.sttReadyToStart){
            binding.recordButton.isEnabled = true
            binding.recordButton.text = "Начать pаспознавание"
        } else {
            binding.recordButton.isEnabled = false
            binding.recordButton.text = "Оборудование не готово"
        }


    }

    override fun onPause() {
        super.onPause()
        recognizerApi?.stopMic()
    }

}
