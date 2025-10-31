package com.pon.speech_to_text_wrapper.internal

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.content.Context.RECEIVER_EXPORTED
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build

@Suppress("DEPRECATION")
class AudioManagerApi(val applicationContext: Application) {
    val am: AudioManager = applicationContext.getSystemService(AUDIO_SERVICE) as AudioManager
    private val receiver = BlueToothSCOConnectionReceiver().also { it.am = am }

    /**
     * Запуск бродкаст ресивера, отслеживающего подключение и отключение аудио блютуз устройств
     * Пока внутри ресивера нет бизнес логики, задачи библиотеки реализуются и без нее
     */
    fun startReceiver(){
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED) // catches bluetooth ON/OFF (the major case)
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED) // catches when the actual bt device connects.
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        intentFilter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        if (Build.VERSION.SDK_INT > 33) applicationContext.registerReceiver(receiver, intentFilter,RECEIVER_EXPORTED)
        else applicationContext.registerReceiver(receiver, intentFilter)
    }

    /**
     * останов бродкаст ресивера, отслеживающего подключение и отключение аудио блютуз устройств
     * Вызывается при очистке и освобождении реcурсов  Api STT
     */
    fun stopReceiver(){
        applicationContext.unregisterReceiver(receiver)
    }


    fun scoState() = am.isBluetoothScoOn

    /**
     * Переводим основное (или единственное) блютуз устройство с параметрами гарнитуры/спикерфона
     * в режим работы приложения с его микрофоном. Вывод звука по умолчанию на это устройство включается
     * автоматически
     */
    fun turnScoOn() {
        // Метод задепрекейчен, но рекомендованный новый что то не заработал, смотри вариант кода ниже.
        // Возможно метод перестанет работать на sdk 35 или 36, тогда надо будет решать эту проблему
        am.startBluetoothSco()
    }
    /*     рекомендованный, но, по итогам тестов, не работающий пока вариант для sdk >=34
         if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val devInfo = am.getDevices(GET_DEVICES_INPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            devInfo?.let {
                am.setCommunicationDevice(it)
            }

        } else
            am.startBluetoothSco()
    }
     */


    @Suppress("DEPRECATION")
    /**
     * Отключаем микрофон как микрофон по умолчанию для приложения для основного  (или единственного)
     * блютуз устройство с параметрами  гарнитуры/спикерфона. Вывод звука по умолчанию на это
     * устройство может быть продолжен до его отключения
     */
    fun turnScoOff() {
        // Метод задепрекейчен, но рекомендованный новый что не заработал, смотри вариант кода ниже
        // возможно метод перестанет работать на sdk 35 или 36, тогда надо будет решать эту проблему
        am.stopBluetoothSco()
    }

}