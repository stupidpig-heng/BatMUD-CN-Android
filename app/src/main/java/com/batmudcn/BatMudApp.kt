package com.batmudcn

import android.app.Application
import android.util.Log

class BatMudApp : Application() {
    companion object {
        private const val TAG = "BatMudApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "BatMUD CN starting...")
    }
}
