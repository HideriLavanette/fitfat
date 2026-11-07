package app.fitfat.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Тактильный и звуковой отклик барабанов, линеек и свайпов — как в макетах:
 * шаг — тихий щелчок 2600 Гц / 25 мс + короткая вибрация; упор в край — глухой 520 Гц / 80 мс + тройной импульс.
 * Не зависит от системных «звуков касания» (их часто выключают). Звук — только в обычном режиме звонка.
 */
object Feedback {
    private const val RATE = 44100
    @Volatile private var tickTrack: AudioTrack? = null
    @Volatile private var bumpTrack: AudioTrack? = null
    private var vibrator: Vibrator? = null
    private var audio: AudioManager? = null

    private fun init(ctx: Context) {
        if (audio != null) return
        val app = ctx.applicationContext
        audio = app.getSystemService(AudioManager::class.java)
        vibrator = if (Build.VERSION.SDK_INT >= 31) app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)
        tickTrack = runCatching { track(2600.0, 0.025, 0.22) }.getOrNull()
        bumpTrack = runCatching { track(520.0, 0.08, 0.45) }.getOrNull()
    }

    /** Синус с экспоненциальным затуханием, 16 бит моно, статический буфер. */
    private fun track(freq: Double, sec: Double, amp: Double): AudioTrack {
        val n = (RATE * sec).toInt()
        val pcm = ShortArray(n) { i ->
            val t = i.toDouble() / RATE
            (sin(2 * PI * freq * t) * amp * exp(-5.0 * t / sec) * Short.MAX_VALUE).toInt().toShort()
        }
        return AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(n * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .apply { write(pcm, 0, n) }
    }

    private fun play(t: AudioTrack?) {
        if (t == null || audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        runCatching { t.pause(); t.flush(); t.playbackHeadPosition = 0; t.play() }
    }

    /** Шаг значения. */
    fun tick(ctx: Context) {
        init(ctx)
        play(tickTrack)
        val v = vibrator ?: return
        runCatching {
            v.vibrate(
                if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                else VibrationEffect.createOneShot(8, 120),
            )
        }
    }

    /** Упор в край, срыв свайпа и прочие «сильные» моменты. */
    fun bump(ctx: Context) {
        init(ctx)
        play(bumpTrack)
        val v = vibrator ?: return
        runCatching {
            v.vibrate(
                if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                else VibrationEffect.createWaveform(longArrayOf(0, 22, 35, 22), intArrayOf(0, 255, 0, 255), -1),
            )
        }
    }

    /** Лёгкий отклик без звука — касание, открытие. */
    fun light(ctx: Context) {
        init(ctx)
        val v = vibrator ?: return
        runCatching {
            v.vibrate(if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK) else VibrationEffect.createOneShot(12, 160))
        }
    }
}
