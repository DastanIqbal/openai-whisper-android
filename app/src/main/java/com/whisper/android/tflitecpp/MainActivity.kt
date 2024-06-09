package com.whisper.android.tflitecpp

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Chronometer
import android.widget.Chronometer.OnChronometerTickListener
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.dastanapps.openai.whisper.Loader.freeModelJNI
import com.dastanapps.openai.whisper.Loader.loadModelJNI

class MainActivity : AppCompatActivity() {
    private var recordAudioButton: ImageView? = null
    private var transcribeResultTxt: TextView? = null
    private var record_chronometer: Chronometer? = null
    private var mRecorder: WavAudioRecorder? = null
    private val recorder: MediaRecorder? = null

    // Requesting permission to RECORD_AUDIO
    private var permissionToRecordAccepted = false
    private val permissions = arrayOf(Manifest.permission.RECORD_AUDIO)

    // Chronometer base and end params
    private val startTime: Long = 0
    private var endTime: Long = 0
    private var recordingDuration: Long = 0
    var timeInMilSeconds: Int = 30000

    // Requesting permission to RECORD_AUDIO
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO_PERMISSION -> permissionToRecordAccepted =
                grantResults[0] == PackageManager.PERMISSION_GRANTED
        }
        if (!permissionToRecordAccepted) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.hide()

        initView()

        // Record to the external cache directory for visibility
        fileName = externalCacheDir?.absolutePath
        fileName += "/android_record.wav"
        Log.e(TAG, fileName ?: "")
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)

        recordAudioButton?.setImageResource(R.drawable.ic_mic_foreground)

        recordAudioButton?.setOnClickListener {
            if (mRecorder == null) {
                mRecorder = WavAudioRecorder.instanse
            }
            if (WavAudioRecorder.State.INITIALIZING == mRecorder?.state) {
                Log.e(TAG, "INITIALIZING" + fileName)
                record_chronometer?.base = SystemClock.elapsedRealtime() + timeInMilSeconds
                record_chronometer?.start()
                mRecorder?.setOutputFile(fileName)
                mRecorder?.prepare()
                mRecorder?.start()
                recordAudioButton?.setImageResource(R.drawable.ic_stop_foreground)
            } else if (WavAudioRecorder.State.ERROR == mRecorder?.state) {
                Log.e(TAG, "ERROR")
                mRecorder?.release()
                mRecorder = WavAudioRecorder.instanse
                mRecorder?.setOutputFile(fileName)
                recordAudioButton?.setImageResource(R.drawable.ic_mic_foreground)
            } else {
                Log.d(TAG, "On Record Stop Click")
                stopRecording()
                startTranscribe()
            }
        }
    }

    private fun stopRecording() {
        record_chronometer?.stop()
        mRecorder?.stop()
        endTime = SystemClock.elapsedRealtime()
        recordingDuration = startTime - endTime
        mRecorder?.reset()
        mRecorder?.release()
        mRecorder = WavAudioRecorder.instanse
        recordAudioButton?.setImageResource(R.drawable.ic_mic_foreground)
    }

    private fun startTranscribe() {
        try {
            Log.e(TAG, fileName ?: "")
            transcribeResultTxt?.text = loadModelJNI(assets, fileName!!, 1)
        } catch (e: Exception) {
            Log.e(TAG, e.message ?: "")
        }
    }

    private fun initView() {
        recordAudioButton = findViewById(R.id.record)
        transcribeResultTxt = findViewById(R.id.result)
        record_chronometer = findViewById(R.id.record_chronometer)
        record_chronometer?.setBase(SystemClock.elapsedRealtime() + timeInMilSeconds)
        record_chronometer?.onChronometerTickListener = OnChronometerTickListener { chronometer ->
            if (chronometer.text.toString().equals("00:00", ignoreCase = true)) {
                chronometer.stop()
                mRecorder?.stop()
                mRecorder?.reset()
                mRecorder?.release()
                mRecorder = WavAudioRecorder.instanse
                recordAudioButton?.setImageResource(R.drawable.ic_mic_foreground)
                startTranscribe()
            }
        }
    }


    public override fun onDestroy() {
        super.onDestroy()
        recorder?.release()
        freeModelJNI()
    }

    companion object {
        private var fileName: String? = null
        private const val TAG = "TFLiteASRDemo"
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }
}