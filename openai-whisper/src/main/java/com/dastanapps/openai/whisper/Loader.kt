package com.dastanapps.openai.whisper

import android.content.res.AssetManager

/**
 *
 * Created by Iqbal Ahmed on 09/06/2024
 *
 */

object Loader {

    init {
        System.loadLibrary("native-lib")
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */

    // Load model by TF Lite C++ API
    external fun loadModelJNI(
        assetManager: AssetManager,
        fileName: String,
        is_recorded: Int
    ): String?

    external fun freeModelJNI(): Int
}