package org.homeguard.core.sensors

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

class CaregiverBeaconManager(private val context: Context) {

    /**
     * Synthesizes a futuristic, gentle two-tone radar chirp (880Hz -> 1760Hz harmonic).
     */
    suspend fun playRadarChirp() = withContext(Dispatchers.Default) {
        val sampleRate = 44100
        val durationMs = 280
        val numSamples = (sampleRate * durationMs) / 1000
        val buffer = ShortArray(numSamples)

        val freqStart = 880.0
        val freqEnd = 1760.0

        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val currentFreq = freqStart + (freqEnd - freqStart) * progress
            val angle = 2.0 * Math.PI * currentFreq * (i.toDouble() / sampleRate)
            // Apply subtle bell envelope
            val envelope = sin(Math.PI * progress)
            buffer[i] = (sin(angle) * 32767.0 * envelope * 0.7).toInt().toShort()
        }

        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
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
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
        } catch (e: Exception) {
            Log.e("BeaconManager", "Failed to play audio beacon: ${e.message}")
        }
    }
}
