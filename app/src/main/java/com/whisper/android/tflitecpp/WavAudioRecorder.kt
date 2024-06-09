package com.whisper.android.tflitecpp;

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

class WavAudioRecorder(
    audioSource: Int,
    sampleRate: Int,
    channelConfig: Int,
    audioFormat: Int
) {
        /**
         * INITIALIZING : recorder is initializing;
         * READY : recorder has been initialized, recorder not yet started
         * RECORDING : recording
         * ERROR : reconstruction needed
         * STOPPED: reset needed
         */
        enum class State {
            INITIALIZING, READY, RECORDING, ERROR, STOPPED
        }

        // Recorder used for uncompressed recording
        private var audioRecorder: AudioRecord? = null

        // Output file path
        private var filePath: String? = null

        /**
         * Returns the state of the recorder in a WavAudioRecorder.State typed object.
         * Useful, as no exceptions are thrown.
         *
         * @return recorder state
         */
        // Recorder state; see State
        var state: State? = null
            private set

        // File writer (only in uncompressed mode)
        private var randomAccessWriter: RandomAccessFile? = null

        // Number of channels, sample rate, sample size(size in bits), buffer size, audio source, sample size(see AudioFormat)
        private var nChannels: Short = 0
        private var sRate = 0
        private var mBitsPersample: Short = 0
        private var mBufferSize = 0
        private var mAudioSource = 0
        private var aFormat = 0

        // Number of frames/samples written to file on each output(only in uncompressed mode)
        private var mPeriodInFrames = 0

        // Buffer for output(only in uncompressed mode)
        private lateinit var buffer: ByteArray

        // Number of bytes written to file after header(only in uncompressed mode)
        // after stop() is called, this size is written to the header/data chunk in the wave file
        private var payloadSize = 0


        private val updateListener: AudioRecord.OnRecordPositionUpdateListener =
            object : AudioRecord.OnRecordPositionUpdateListener {
                //	periodic updates on the progress of the record head
                override fun onPeriodicNotification(recorder: AudioRecord) {
                    if (State.STOPPED == state) {
                        Log.d(this@WavAudioRecorder.javaClass.name, "recorder stopped")
                        return
                    }
                    val numOfBytes =
                        audioRecorder?.read(buffer, 0, buffer.size) // read audio data to buffer
                    //			Log.d(WavAudioRecorder.this.getClass().getName(), state + ":" + numOfBytes);
                    try {
                        randomAccessWriter?.write(buffer) // write audio data to file
                        payloadSize += buffer.size
                    } catch (e: IOException) {
                        Log.e(
                            WavAudioRecorder::class.java.name,
                            "Error occured in updateListener, recording is aborted"
                        )
                        e.printStackTrace()
                    }
                }

                //	reached a notification marker set by setNotificationMarkerPosition(int)
                override fun onMarkerReached(recorder: AudioRecord) {
                }
            }

        /**
         * Default constructor
         *
         *
         * Instantiates a new recorder
         * In case of errors, no exception is thrown, but the state is set to ERROR
         */
        init {
            try {
                mBitsPersample = if (audioFormat == AudioFormat.ENCODING_PCM_16BIT) {
                    16
                } else {
                    8
                }

                nChannels = if (channelConfig == AudioFormat.CHANNEL_IN_MONO) {
                    1
                } else {
                    2
                }

                mAudioSource = audioSource
                sRate = sampleRate
                aFormat = audioFormat

                mPeriodInFrames = sampleRate * TIMER_INTERVAL / 1000 //?
                mBufferSize = mPeriodInFrames * 2 * nChannels * mBitsPersample / 8 //?
                if (mBufferSize < AudioRecord.getMinBufferSize(
                        sampleRate,
                        channelConfig,
                        audioFormat
                    )
                ) {
                    // Check to make sure buffer size is not smaller than the smallest allowed one
                    mBufferSize =
                        AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                    // Set frame period and timer interval accordingly
                    mPeriodInFrames = mBufferSize / (2 * mBitsPersample * nChannels / 8)
                    Log.w(
                        WavAudioRecorder::class.java.name,
                        "Increasing buffer size to $mBufferSize"
                    )
                }

                audioRecorder =
                    AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, mBufferSize)

                if (audioRecorder?.state != AudioRecord.STATE_INITIALIZED) {
                    throw Exception("AudioRecord initialization failed")
                }
                audioRecorder?.setRecordPositionUpdateListener(updateListener)
                audioRecorder?.setPositionNotificationPeriod(mPeriodInFrames)
                filePath = null
                state = State.INITIALIZING
            } catch (e: Exception) {
                if (e.message != null) {
                    Log.e(WavAudioRecorder::class.java.name, e.message?:"")
                } else {
                    Log.e(
                        WavAudioRecorder::class.java.name,
                        "Unknown error occured while initializing recording"
                    )
                }
                state = State.ERROR
            }
        }

        /**
         * Sets output file path, call directly after construction/reset.
         *
         * @param output file path
         */
        fun setOutputFile(argPath: String?) {
            try {
                if (state == State.INITIALIZING) {
                    filePath = argPath
                }
            } catch (e: Exception) {
                if (e.message != null) {
                    Log.e(WavAudioRecorder::class.java.name, e.message?:"")
                } else {
                    Log.e(
                        WavAudioRecorder::class.java.name,
                        "Unknown error occured while setting output path"
                    )
                }
                state = State.ERROR
            }
        }


        /**
         * Prepares the recorder for recording, in case the recorder is not in the INITIALIZING state and the file path was not set
         * the recorder is set to the ERROR state, which makes a reconstruction necessary.
         * In case uncompressed recording is toggled, the header of the wave file is written.
         * In case of an exception, the state is changed to ERROR
         */
        fun prepare() {
            try {
                if (state == State.INITIALIZING) {
                    if ((audioRecorder?.state == AudioRecord.STATE_INITIALIZED) and (filePath != null)) {
                        // write file header
                        randomAccessWriter = RandomAccessFile(filePath, "rw")
                        randomAccessWriter?.setLength(0) // Set file length to 0, to prevent unexpected behavior in case the file already existed
                        randomAccessWriter?.writeBytes("RIFF")
                        randomAccessWriter?.writeInt(0) // Final file size not known yet, write 0
                        randomAccessWriter?.writeBytes("WAVE")
                        randomAccessWriter?.writeBytes("fmt ")
                        randomAccessWriter?.writeInt(Integer.reverseBytes(16)) // Sub-chunk size, 16 for PCM
                        randomAccessWriter?.writeShort(
                            java.lang.Short.reverseBytes(1.toShort()).toInt()
                        ) // AudioFormat, 1 for PCM
                        randomAccessWriter?.writeShort(
                            java.lang.Short.reverseBytes(nChannels).toInt()
                        ) // Number of channels, 1 for mono, 2 for stereo
                        randomAccessWriter?.writeInt(Integer.reverseBytes(sRate)) // Sample rate
                        randomAccessWriter?.writeInt(Integer.reverseBytes(sRate * nChannels * mBitsPersample / 8)) // Byte rate, SampleRate*NumberOfChannels*mBitsPersample/8
                        randomAccessWriter?.writeShort(
                            java.lang.Short.reverseBytes((nChannels * mBitsPersample / 8).toShort())
                                .toInt()
                        ) // Block align, NumberOfChannels*mBitsPersample/8
                        randomAccessWriter?.writeShort(
                            java.lang.Short.reverseBytes(mBitsPersample).toInt()
                        ) // Bits per sample
                        randomAccessWriter?.writeBytes("data")
                        randomAccessWriter?.writeInt(0) // Data chunk size not known yet, write 0
                        buffer = ByteArray(mPeriodInFrames * mBitsPersample / 8 * nChannels)
                        state = State.READY
                    } else {
                        Log.e(
                            WavAudioRecorder::class.java.name,
                            "prepare() method called on uninitialized recorder"
                        )
                        state = State.ERROR
                    }
                } else {
                    Log.e(
                        WavAudioRecorder::class.java.name,
                        "prepare() method called on illegal state"
                    )
                    release()
                    state = State.ERROR
                }
            } catch (e: Exception) {
                if (e.message != null) {
                    Log.e(WavAudioRecorder::class.java.name, e.message?:"")
                } else {
                    Log.e(WavAudioRecorder::class.java.name, "Unknown error occured in prepare()")
                }
                state = State.ERROR
            }
        }

        /**
         * Releases the resources associated with this class, and removes the unnecessary files, when necessary
         */
        fun release() {
            if (state == State.RECORDING) {
                stop()
            } else {
                if (state == State.READY) {
                    try {
                        randomAccessWriter?.close() // Remove prepared file
                    } catch (e: IOException) {
                        Log.e(
                            WavAudioRecorder::class.java.name,
                            "I/O exception occured while closing output file"
                        )
                    }
                    File(filePath).delete()
                }
            }

            if (audioRecorder != null) {
                audioRecorder?.release()
            }
        }

        /**
         * Resets the recorder to the INITIALIZING state, as if it was just created.
         * In case the class was in RECORDING state, the recording is stopped.
         * In case of exceptions the class is set to the ERROR state.
         */
        fun reset() {
            try {
                if (state != State.ERROR) {
                    release()
                    filePath = null // Reset file path
                    audioRecorder =
                        AudioRecord(mAudioSource, sRate, nChannels.toInt(), aFormat, mBufferSize)
                    if (audioRecorder?.state != AudioRecord.STATE_INITIALIZED) {
                        throw Exception("AudioRecord initialization failed")
                    }
                    audioRecorder?.setRecordPositionUpdateListener(updateListener)
                    audioRecorder?.setPositionNotificationPeriod(mPeriodInFrames)
                    state = State.INITIALIZING
                }
            } catch (e: Exception) {
                Log.e(WavAudioRecorder::class.java.name, e.message?:"")
                state = State.ERROR
            }
        }

        /**
         * Starts the recording, and sets the state to RECORDING.
         * Call after prepare().
         */
        fun start() {
            if (state == State.READY) {
                payloadSize = 0
                audioRecorder?.startRecording()
                audioRecorder?.read(
                    buffer,
                    0,
                    buffer.size
                ) //[TODO: is this necessary]read the existing data in audio hardware, but don't do anything
                state = State.RECORDING
            } else {
                Log.e(WavAudioRecorder::class.java.name, "start() called on illegal state")
                state = State.ERROR
            }
        }

        /**
         * Stops the recording, and sets the state to STOPPED.
         * In case of further usage, a reset is needed.
         * Also finalizes the wave file in case of uncompressed recording.
         */
        fun stop() {
            if (state == State.RECORDING) {
                audioRecorder?.stop()
                try {
                    randomAccessWriter?.seek(4) // Write size to RIFF header
                    randomAccessWriter?.writeInt(Integer.reverseBytes(36 + payloadSize))

                    randomAccessWriter?.seek(40) // Write size to Subchunk2Size field
                    randomAccessWriter?.writeInt(Integer.reverseBytes(payloadSize))

                    randomAccessWriter?.close()
                } catch (e: IOException) {
                    Log.e(
                        WavAudioRecorder::class.java.name,
                        "I/O exception occured while closing output file"
                    )
                    state = State.ERROR
                }
                state = State.STOPPED
            } else {
                Log.e(WavAudioRecorder::class.java.name, "stop() called on illegal state")
                state = State.ERROR
            }
        }

        companion object {
            private val sampleRates = intArrayOf(44100, 22050, 16000, 11025, 8000)

            val instanse: WavAudioRecorder?
                get() {
                    var result: WavAudioRecorder? = null
                    var i = 2
                    do {
                        result = WavAudioRecorder(
                            MediaRecorder.AudioSource.MIC,
                            sampleRates[i],
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                        )
                    } while ((++i < sampleRates.size) and (result?.state != State.INITIALIZING))
                    return result
                }

            const val RECORDING_UNCOMPRESSED: Boolean = true
            const val RECORDING_COMPRESSED: Boolean = false

            // The interval in which the recorded samples are output to the file
            // Used only in uncompressed mode
            private const val TIMER_INTERVAL = 120
        }
    }