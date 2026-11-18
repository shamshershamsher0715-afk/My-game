package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Procedural 4-cylinder superbike engine sound synthesizer using Android AudioTrack.
 * Generates multi-harmonic exhaust resonance + intake scream modulated by live RPM & boost.
 */
class EngineSoundSynthesizer {

    @Volatile
    private var isRunning = false
    @Volatile
    private var isMuted = true
    @Volatile
    private var targetRpmNorm = 0.15f
    @Volatile
    private var targetSpeedNorm = 0f
    @Volatile
    private var isBoosting = false

    private var audioThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    fun start() {
        if (isRunning) return
        isRunning = true

        val sampleRate = 22050
        val minBufBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(1024)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufBytes * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        try {
            track.play()
        } catch (_: IllegalStateException) {
            isRunning = false
            return
        }

        audioThread = Thread {
            val chunkSamples = 512
            val buffer = ShortArray(chunkSamples)
            var phase1 = 0.0
            var phase2 = 0.0
            var phase3 = 0.0
            var noiseState = 1337
            var smoothRpm = 0.15f

            while (isRunning) {
                if (isMuted) {
                    buffer.fill(0)
                    try {
                        track.write(buffer, 0, chunkSamples)
                        Thread.sleep(24)
                    } catch (_: Throwable) {
                        break
                    }
                    continue
                }

                val desiredRpm = targetRpmNorm.coerceIn(0.1f, 1.05f)
                val boostActive = isBoosting
                val spd = targetSpeedNorm.coerceIn(0f, 1f)

                for (i in 0 until chunkSamples) {
                    smoothRpm += (desiredRpm - smoothRpm) * 0.004f
                    // Inline-4 firing frequency: ~45Hz idle up to ~245Hz at 14,500 RPM
                    val baseFreq = 46.0 + smoothRpm * 198.0 + if (boostActive) 18.0 else 0.0
                    val step1 = (2.0 * PI * baseFreq) / sampleRate
                    val step2 = (2.0 * PI * (baseFreq * 2.0)) / sampleRate
                    val step3 = (2.0 * PI * (baseFreq * 3.0)) / sampleRate

                    phase1 = (phase1 + step1) % (2.0 * PI)
                    phase2 = (phase2 + step2) % (2.0 * PI)
                    phase3 = (phase3 + step3) % (2.0 * PI)

                    // Pseudo-random wind & exhaust air rush
                    noiseState = (noiseState * 1664525 + 1013904223)
                    val whiteNoise = ((noiseState ushr 16) and 0xFFFF) / 32768.0 - 1.0

                    val engineTone =
                        0.48 * sin(phase1) +
                        0.30 * sin(phase2) +
                        0.16 * sin(phase3) +
                        0.08 * sin(phase1 * 0.5) // sub-harmonic rumble

                    // Soft-clipping exhaust saturation
                    val saturated = (engineTone * (1.2 + smoothRpm * 0.6)).coerceIn(-1.0, 1.0)
                    val windMix = whiteNoise * (0.04 + spd * 0.14 + if (boostActive) 0.10 else 0.0)
                    val gain = 0.16 + smoothRpm * 0.12
                    val sample = ((saturated * gain) + windMix).coerceIn(-0.85, 0.85)
                    buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                }

                try {
                    track.write(buffer, 0, chunkSamples)
                } catch (_: Throwable) {
                    break
                }
            }
        }.apply {
            name = "ApexEngineAudio"
            isDaemon = true
            start()
        }
    }

    fun update(rpmNormalized: Float, speedNormalized: Float, boosting: Boolean, activeAndEnabled: Boolean) {
        targetRpmNorm = rpmNormalized
        targetSpeedNorm = speedNormalized
        isBoosting = boosting
        isMuted = !activeAndEnabled
    }

    fun release() {
        isRunning = false
        isMuted = true
        try {
            audioThread?.join(150)
        } catch (_: InterruptedException) {
        }
        audioThread = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Throwable) {
        }
        audioTrack = null
    }
}
