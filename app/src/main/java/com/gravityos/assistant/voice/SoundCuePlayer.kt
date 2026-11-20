package com.gravityos.assistant.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

object SoundCuePlayer {

    private const val SAMPLE_RATE = 24000

    fun playWakeChime() {
        playTones(
            listOf(
                ToneSpec(freqHz = 540.0, durationMs = 75, volume = 0.35f),
                ToneSpec(freqHz = 820.0, durationMs = 85, volume = 0.42f),
                ToneSpec(freqHz = 1140.0, durationMs = 110, volume = 0.36f)
            )
        )
    }

    fun playCommandDoneCue() {
        playTones(
            listOf(
                ToneSpec(freqHz = 740.0, durationMs = 80, volume = 0.28f),
                ToneSpec(freqHz = 980.0, durationMs = 95, volume = 0.25f)
            )
        )
    }

    fun playSoftErrorCue() {
        playTones(
            listOf(
                ToneSpec(freqHz = 320.0, durationMs = 110, volume = 0.30f),
                ToneSpec(freqHz = 250.0, durationMs = 130, volume = 0.28f)
            )
        )
    }

    private data class ToneSpec(val freqHz: Double, val durationMs: Int, val volume: Float)

    private fun playTones(tones: List<ToneSpec>) {
        Thread {
            try {
                val totalSamples = tones.sumOf { (SAMPLE_RATE * it.durationMs) / 1000 }
                val pcm = ShortArray(totalSamples)
                var offset = 0

                for (tone in tones) {
                    val count = (SAMPLE_RATE * tone.durationMs) / 1000
                    val fadeSamples = (count * 0.18).toInt().coerceAtLeast(1)
                    for (i in 0 until count) {
                        val env = when {
                            i < fadeSamples -> i.toFloat() / fadeSamples
                            i > count - fadeSamples -> (count - i).toFloat() / fadeSamples
                            else -> 1f
                        }
                        val angle = 2.0 * PI * i * tone.freqHz / SAMPLE_RATE
                        val sample = (sin(angle) * Short.MAX_VALUE * tone.volume * env).toInt()
                        pcm[offset + i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                    offset += count
                }

                val bufferBytes = pcm.size * 2
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(bufferBytes, minBuf))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.play()
                val sleepMs = tones.sumOf { it.durationMs }.toLong() + 80L
                Thread.sleep(sleepMs)
                track.stop()
                track.release()
            } catch (_: Throwable) {
            }
        }.start()
    }
}
