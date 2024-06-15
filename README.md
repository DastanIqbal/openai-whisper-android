## Android app with Whisper

Use openai-whisper model offline with Android app.

### Modules
Created a separate library module to plugin in any app.

- AAR module library_openai-whisper-android
- Library Module openai-whisper-android

### Usage
#### Record Audio
Use `WavAudioRecorder.kt` to record audio, check [sample](https://github.com/DastanIqbal/openai-whisper-android/blob/master/app/src/main/java/com/whisper/android/tflitecpp/MainActivity.kt)
```kotlin
    var mRecorder: WavAudioRecorder? = null
    // Start Recording
    mRecorder = WavAudioRecorder.instanse
    mRecorder?.setOutputFile(fileName)
    mRecorder?.prepare()
    mRecorder?.start()

    // Stop Recording
    mRecorder?.release()
    mRecorder = WavAudioRecorder.instanse
    mRecorder?.setOutputFile(fileName)
```

#### Transcribe audio to text
```kotlin
    // assets AssetManager(asset folder)
    // filePath: path of the file (.wav)
    // isRecorded 
    Loader.loadModelJNI(assets, filePath, isRecorded)
```

#### Download AAR File
[library_openai-whisper-android.aar](https://github.com/DastanIqbal/openai-whisper-android/raw/master/download/library_openai-whisper-android.aar)

#### Download AAR Module
[library_openai-whisper-android.zip AAR Module](https://github.com/DastanIqbal/openai-whisper-android/raw/master/download/library_openai-whisper-android.zip)

#### Download Library Module
[openai-whisper-android.zip Module](https://github.com/DastanIqbal/openai-whisper-android/raw/master/download/openai-whisper-android.zip)


### Demo
Add Video

### References

[Original Work](https://github.com/usefulsensors/openai-whisper)

#### How to use Whisper TFLIte using TF Lite C++ library in Android projects

This repo is an example that transcribe audio using whisper.tflite model in Android projects

