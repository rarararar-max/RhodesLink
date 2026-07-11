package com.rhodes.privatechat.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.os.Build
import java.io.File
import kotlin.concurrent.thread

class LocalAudioController(private val context: Context) {
    private var recorder: AudioRecord? = null
    private var player: MediaPlayer? = null
    private var recordingFile: File? = null
    private var startedAt: Long = 0L
    @Volatile private var isRecording = false
    private var recordingThread: Thread? = null
    private val pcmChunks = mutableListOf<ByteArray>()
    private val pcmLock = Any()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (isRecording) stopRecordingInternal()
        stopPlayback()
        val file = File(File(context.filesDir, "voice").apply { mkdirs() }, "voice_${System.currentTimeMillis()}.wav")
        val minBufferSize = AudioRecord.getMinBufferSize(RECORD_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBufferSize <= 0) return false
        return runCatching {
            val bufferSize = minBufferSize.coerceAtLeast(RECORD_SAMPLE_RATE / 2)
            val audioRecord = AudioRecord(android.media.MediaRecorder.AudioSource.MIC, RECORD_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                error("AudioRecord init failed")
            }
            synchronized(pcmLock) { pcmChunks.clear() }
            recorder = audioRecord
            recordingFile = file
            startedAt = System.currentTimeMillis()
            isRecording = true
            audioRecord.startRecording()
            recordingThread = thread(name = "rhodes-audio-record") {
                val buffer = ByteArray(bufferSize)
                while (isRecording) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) synchronized(pcmLock) { pcmChunks += buffer.copyOf(read) }
                }
            }
            true
        }.getOrElse {
            stopRecordingInternal()
            recordingFile = null
            false
        }
    }

    fun stopRecording(): RecordedAudio? {
        val file = recordingFile ?: return null
        val durationMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(300L)
        stopRecordingInternal()
        val pcmBytes = synchronized(pcmLock) {
            val output = ByteArray(pcmChunks.sumOf { it.size })
            var offset = 0
            pcmChunks.forEach { chunk -> chunk.copyInto(output, destinationOffset = offset); offset += chunk.size }
            pcmChunks.clear()
            output
        }
        recordingFile = null
        if (pcmBytes.isEmpty()) return null
        file.writeBytes(buildWavFile(pcmBytes, RECORD_SAMPLE_RATE, 1, 16))
        return RecordedAudio(file.absolutePath, durationMs)
    }

    fun readPcmFromWav(path: String): ByteArray {
        val bytes = File(path.removePrefix("file://")).readBytes()
        return if (bytes.size <= WAV_HEADER_SIZE) ByteArray(0) else bytes.copyOfRange(WAV_HEADER_SIZE, bytes.size)
    }

    fun saveTtsAudio(bytes: ByteArray, extension: String = "mp3"): RecordedAudio? {
        if (bytes.isEmpty()) return null
        val file = File(File(context.filesDir, "voice").apply { mkdirs() }, "tts_${System.currentTimeMillis()}.$extension")
        file.writeBytes(bytes)
        return RecordedAudio(file.absolutePath, (bytes.size / 16L).coerceIn(800L, 120_000L))
    }

    fun play(path: String, onComplete: ((Boolean) -> Unit)? = null) {
        stopPlayback()
        val file = File(path.removePrefix("file://"))
        runCatching {
            requestPlaybackFocus()
            player = MediaPlayer().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                } else {
                    @Suppress("DEPRECATION") setAudioStreamType(AudioManager.STREAM_MUSIC)
                }
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener { stopPlayback(); onComplete?.invoke(true) }
                setOnErrorListener { _, _, _ -> stopPlayback(); onComplete?.invoke(false); true }
                start()
            }
        }.getOrElse { stopPlayback(); onComplete?.invoke(false) }
    }

    fun stopPlayback() {
        runCatching { player?.stop() }
        player?.release()
        player = null
        abandonPlaybackFocus()
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        @Suppress("DEPRECATION") audioManager.isSpeakerphoneOn = enabled
        audioManager.mode = if (enabled) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
    }

    private fun stopRecordingInternal() {
        isRecording = false
        recordingThread?.join(500L)
        recordingThread = null
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
    }

    private fun requestPlaybackFocus(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { }
            .build()
        audioFocusRequest = request
        audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    } else {
        @Suppress("DEPRECATION") audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonPlaybackFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION") audioManager.abandonAudioFocus(null)
        }
    }

    private fun buildWavFile(pcm: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val output = ByteArray(WAV_HEADER_SIZE + pcm.size)
        fun writeAscii(offset: Int, value: String) { value.forEachIndexed { index, char -> output[offset + index] = char.code.toByte() } }
        fun writeIntLe(offset: Int, value: Int) { output[offset] = (value and 0xff).toByte(); output[offset + 1] = ((value shr 8) and 0xff).toByte(); output[offset + 2] = ((value shr 16) and 0xff).toByte(); output[offset + 3] = ((value shr 24) and 0xff).toByte() }
        fun writeShortLe(offset: Int, value: Int) { output[offset] = (value and 0xff).toByte(); output[offset + 1] = ((value shr 8) and 0xff).toByte() }
        writeAscii(0, "RIFF"); writeIntLe(4, 36 + pcm.size); writeAscii(8, "WAVE"); writeAscii(12, "fmt "); writeIntLe(16, 16)
        writeShortLe(20, 1); writeShortLe(22, channels); writeIntLe(24, sampleRate); writeIntLe(28, byteRate); writeShortLe(32, blockAlign); writeShortLe(34, bitsPerSample)
        writeAscii(36, "data"); writeIntLe(40, pcm.size); pcm.copyInto(output, destinationOffset = WAV_HEADER_SIZE)
        return output
    }
}

private const val RECORD_SAMPLE_RATE = 16_000
private const val WAV_HEADER_SIZE = 44

data class RecordedAudio(val path: String, val durationMs: Long)
