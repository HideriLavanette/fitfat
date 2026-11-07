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
 * шаг — тихий глухой щелчок, как у барабана револьвера (≈ 30 мс) + короткая вибрация;
 * упор в край — более низкий и плотный «тук» (≈ 90 мс) + сильная вибрация.
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
        tickTrack = runCatching { track(click(0.030, noiseCut = 3200.0, noiseTau = 0.0012, ring = 1300.0, ringTau = 0.0035, thump = 240.0, thumpTau = 0.007, peak = 0.13)) }.getOrNull()
        bumpTrack = runCatching { track(click(0.090, noiseCut = 1500.0, noiseTau = 0.003, ring = 620.0, ringTau = 0.006, thump = 130.0, thumpTau = 0.022, peak = 0.30)) }.getOrNull()
    }

    /**
     * Механический щелчок: короткий шорох с срезанными верхами («тк»), негромкий металлический
     * резонанс и низкий «стук» корпуса. Всё быстро затухает; мягкая атака 0,3 мс — без хлопка.
     */
    private fun click(
        sec: Double, noiseCut: Double, noiseTau: Double, ring: Double, ringTau: Double,
        thump: Double, thumpTau: Double, peak: Double,
    ): ShortArray {
        val n = (RATE * sec).toInt()
        val rnd = java.util.Random(7)
        val a = exp(-2 * PI * noiseCut / RATE) // однополюсный ФНЧ для шороха
        var lp = 0.0
        val raw = DoubleArray(n) { i ->
            val t = i.toDouble() / RATE
            lp = (1 - a) * (rnd.nextDouble() * 2 - 1) + a * lp
            val attack = (t / 0.0003).coerceAtMost(1.0)
            attack * (
                lp * 2.2 * exp(-t / noiseTau) +
                    0.45 * sin(2 * PI * ring * t) * exp(-t / ringTau) +
                    0.75 * sin(2 * PI * thump * t) * exp(-t / thumpTau)
                )
        }
        // Плавный хвост к нулю и нормировка по пику
        for (i in 0 until n) raw[i] *= ((n - i).toDouble() / (n * 0.15)).coerceAtMost(1.0)
        val k = peak / (raw.maxOf { kotlin.math.abs(it) }.takeIf { it > 0 } ?: 1.0)
        return ShortArray(n) { i -> (raw[i] * k * Short.MAX_VALUE).toInt().toShort() }
    }

    /** 16 бит моно, статический буфер. */
    private fun track(pcm: ShortArray): AudioTrack {
        val n = pcm.size
        return AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(n * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .apply { write(pcm, 0, n) }
    }

    /**
     * Вибрация как «имитация физического предмета» (Android 13+): барабаны и линейки — щелчки колёсика.
     * Обычная вибрация касания (usage TOUCH) глушится, если в системе выключен «Отклик на касание», —
     * а в приложении щелчки шагов нужны всегда. Эту гасит только выключенная вибрация оборудования.
     */
    private fun buzz(effect: VibrationEffect) {
        val v = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) v.vibrate(effect, android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_PHYSICAL_EMULATION))
            else v.vibrate(effect)
        }
    }

    private fun play(t: AudioTrack?) {
        if (t == null || audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        runCatching { t.pause(); t.flush(); t.playbackHeadPosition = 0; t.play() }
    }

    /** Шаг значения. */
    fun tick(ctx: Context) {
        init(ctx)
        play(tickTrack)
        buzz(
            if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                else VibrationEffect.createOneShot(8, 120),
        )
    }

    /** Шаг барабана, линейки, круга веса: тот же щелчок, вибрация вполсилы — на частых шагах полная сила утомляет. */
    fun step(ctx: Context) {
        init(ctx)
        play(tickTrack)
        val v = vibrator ?: return
        buzz(halfTick(v))
    }

    @Volatile private var half: VibrationEffect? = null

    /** Половина обычного «тика»: примитив TICK с силой 0,5, без примитивов — короткий импульс половинной амплитуды. */
    private fun halfTick(v: Vibrator): VibrationEffect = half ?: run {
        when {
            Build.VERSION.SDK_INT >= 30 && v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK) ->
                VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f).compose()
            v.hasAmplitudeControl() -> VibrationEffect.createOneShot(8, 60)
            Build.VERSION.SDK_INT >= 29 -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            else -> VibrationEffect.createOneShot(8, 60)
        }.also { half = it }
    }

    /** Шаг без звука — только короткая вибрация (навбар). */
    fun tickSilent(ctx: Context) {
        init(ctx)
        buzz(
            if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                else VibrationEffect.createOneShot(8, 120),
        )
    }

    /** Упор в край, срыв свайпа и прочие «сильные» моменты. */
    fun bump(ctx: Context) {
        init(ctx)
        play(bumpTrack)
        buzz(
            if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                else VibrationEffect.createWaveform(longArrayOf(0, 22, 35, 22), intArrayOf(0, 255, 0, 255), -1),
        )
    }

    /** Лёгкий отклик без звука — касание, открытие. */
    fun light(ctx: Context) {
        init(ctx)
        buzz(if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK) else VibrationEffect.createOneShot(12, 160))
    }
}
