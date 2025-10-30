package net.gotev.speech;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import net.gotev.speech.engine.BaseTextToSpeechEngine;
import net.gotev.speech.engine.DummyOnInitListener;
import net.gotev.speech.engine.TextToSpeechEngine;

import java.util.List;
import java.util.Locale;

/**
 * Helper class to easily work with Android speech recognition.
 *
 * @author Aleksandar Gotev
 */
public class Speech {

    private static Speech instance = null;
    protected static String GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox";
    private final Context mContext;
    private final TextToSpeechEngine textToSpeechEngine;
    private Speech(final Context context, TextToSpeech.OnInitListener onInitListener, TextToSpeechEngine textToSpeechEngine) {
        mContext = context;
        this.textToSpeechEngine = textToSpeechEngine;
        this.textToSpeechEngine.setOnInitListener(onInitListener);
        this.textToSpeechEngine.initTextToSpeech(context);
    }

    /**
     * Initializes speech recognition.
     *
     * @param context application context
     * @return speech instance
     */
    public static Speech init(final Context context) {
        if (instance == null) {
            instance = new Speech(context, new DummyOnInitListener(), new BaseTextToSpeechEngine());
        }
        return instance;
    }



    public static Speech init(final Context context, TextToSpeech.OnInitListener onInitListener) {
        if (instance == null) {
            instance = new Speech(context, onInitListener,  new BaseTextToSpeechEngine());
        }

        return instance;
    }



    /**
     * Must be called inside Activity's onDestroy.
     */
    public synchronized void shutdown() {
        textToSpeechEngine.shutdown();
        instance = null;
    }

    /**
     * Gets speech recognition instance.
     *
     * @return SpeechRecognition instance
     */
    public static Speech getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Speech has not been initialized!" +
                    " Call init method first!");
        }

        return instance;
    }





    /**
     * Check if text to speak is currently speaking.
     *
     * @return true if the text to speak is speaking, false otherwise
     */
    public boolean isSpeaking() {
        return textToSpeechEngine.isSpeaking();
    }

    /**
     * Uses text to speech to transform a written message into a sound.
     *
     * @param message message to play
     */
    public void say(final String message) {
        say(message, null);
    }

    /**
     * Uses text to speech to transform a written message into a sound.
     *
     * @param message  message to play
     * @param callback callback which will receive progress status of the operation
     */
    public void say(final String message, final TextToSpeechCallback callback) {
        textToSpeechEngine.say(message, callback);
    }

    /**
     * Stops text to speech.
     */
    public void stopTextToSpeech() {
        textToSpeechEngine.stop();
    }




    /**
     * Sets text to speech and recognition language.
     * Defaults to device language setting.
     *
     * @param locale new locale
     * @return speech instance
     */
    public Speech setLocale(final Locale locale) {

        textToSpeechEngine.setLocale(locale);
        return this;
    }

    /**
     * Sets the speech rate. This has no effect on any pre-recorded speech.
     *
     * @param rate Speech rate. 1.0 is the normal speech rate, lower values slow down the speech
     *             (0.5 is half the normal speech rate), greater values accelerate it
     *             (2.0 is twice the normal speech rate).
     * @return speech instance
     */
    public Speech setTextToSpeechRate(final float rate) {
        textToSpeechEngine.setSpeechRate(rate);
        return this;
    }

    /**
     * Sets the voice for the TextToSpeech engine.
     * This has no effect on any pre-recorded speech.
     *
     * @param voice Speech voice.
     * @return speech instance
     */
    public Speech setVoice(final Voice voice) {
        textToSpeechEngine.setVoice(voice);
        return this;
    }

    /**
     * Sets the speech pitch for the TextToSpeech engine.
     * This has no effect on any pre-recorded speech.
     *
     * @param pitch Speech pitch. 1.0 is the normal pitch, lower values lower the tone of the
     *              synthesized voice, greater values increase it.
     * @return speech instance
     */
    public Speech setTextToSpeechPitch(final float pitch) {
        textToSpeechEngine.setPitch(pitch);
        return this;
    }




    /**
     * Sets the text to speech queue mode.
     * By default is TextToSpeech.QUEUE_FLUSH, which is faster, because it clears all the
     * messages before speaking the new one. TextToSpeech.QUEUE_ADD adds the last message
     * to speak in the queue, without clearing the messages that have been added.
     *
     * @param mode It can be either TextToSpeech.QUEUE_ADD or TextToSpeech.QUEUE_FLUSH.
     * @return speech instance
     */
    public Speech setTextToSpeechQueueMode(final int mode) {
        textToSpeechEngine.setTextToSpeechQueueMode(mode);
        return this;
    }

    /**
     * Sets the audio stream type.
     * By default is TextToSpeech.Engine.DEFAULT_STREAM, which is equivalent to
     * AudioManager.STREAM_MUSIC.
     *
     * @param audioStream A constant from AudioManager.
     *                    e.g. {@link android.media.AudioManager#STREAM_VOICE_CALL}
     * @return speech instance
     */
    public Speech setAudioStream(final int audioStream) {
        textToSpeechEngine.setAudioStream(audioStream);
        return this;
    }

    boolean isGoogleAppInstalled() {
        PackageManager packageManager = mContext.getPackageManager();
        for (PackageInfo packageInfo : packageManager.getInstalledPackages(0)) {
            if (packageInfo.packageName.contains(GOOGLE_APP_PACKAGE)) {
                return true;
            }
        }
        return false;
    }



    /**
     * Gets the list of the supported Text to Speech languages on this device
     *
     * @return list of locales on android API 23 and newer and empty list on lower Android, because native
     * TTS engine does not support querying voices on API lower than 23. Officially it's declared that
     * query voices support started on API 21, but in reality it started from 23.
     * If still skeptic about this, search the web and try on your own.
     */
    public List<Voice> getSupportedTextToSpeechVoices() {
        return textToSpeechEngine.getSupportedVoices();
    }



    /**
     * Gets the current voice used for text to speech.
     *
     * @return current voice on android API 23 or newer and null on lower Android, because native
     * TTS engine does not support querying voices on API lower than 23. Officially it's declared that
     * query voices support started on API 21, but in reality it started from 23.
     * If still skeptic about this, search the web and try on your own.
     */
    public Voice getTextToSpeechVoice() {
        return textToSpeechEngine.getCurrentVoice();
    }

}
