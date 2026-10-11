package com.nexappra.filerecovery

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import java.io.File

/** Real, locally generated containers: no downloads, camera or microphone access. */
internal object RecoveryVideoFixtures {
    fun video(directory: File): ByteArray {
        val width = 320
        val height = 240
        val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 5)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        return encode(directory, format, ByteArray(width * height * 3 / 2) { 128.toByte() }, 200_000L)
    }

    fun audioOnlyMp4(directory: File): ByteArray {
        val format = MediaFormat.createAudioFormat("audio/mp4a-latm", 44_100, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 2_048)
        }
        return encode(directory, format, ByteArray(2_048), 1_024_000_000L / 44_100)
    }

    private fun encode(directory: File, format: MediaFormat, frame: ByteArray, frameTimeUs: Long): ByteArray {
        val outputFile = File.createTempFile("media-fixture-", ".mp4", directory)
        val encoder = MediaCodec.createEncoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
        val muxer = MediaMuxer(outputFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var track = -1
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            val info = MediaCodec.BufferInfo()
            var submitted = 0
            var finished = false
            val deadline = SystemClock.elapsedRealtime() + 15_000L
            while (!finished) {
                check(SystemClock.elapsedRealtime() < deadline) { "Media fixture encoder timed out" }
                if (submitted <= 3) {
                    val inputIndex = encoder.dequeueInputBuffer(0)
                    if (inputIndex >= 0) {
                        val eos = submitted == 3
                        if (!eos) checkNotNull(encoder.getInputBuffer(inputIndex)).apply { clear(); put(frame) }
                        encoder.queueInputBuffer(inputIndex, 0, if (eos) 0 else frame.size,
                            submitted * frameTimeUs, if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                        submitted++
                    }
                }
                when (val outputIndex = encoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted)
                        track = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    else -> if (outputIndex >= 0) {
                        try {
                            if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                check(muxerStarted)
                                val buffer = checkNotNull(encoder.getOutputBuffer(outputIndex))
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                muxer.writeSampleData(track, buffer, info)
                            }
                            finished = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        } finally { encoder.releaseOutputBuffer(outputIndex, false) }
                    }
                }
            }
        } finally {
            runCatching { encoder.stop() }
            encoder.release()
            try { if (muxerStarted) muxer.stop() } finally { muxer.release() }
        }
        return outputFile.readBytes()
    }
}
