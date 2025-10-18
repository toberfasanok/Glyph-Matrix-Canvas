package com.tober.glyphmatrix.canvas

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcelable
import android.util.Log

import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject

class GlyphMatrixService : Service() {
    private val tag = "Glyph Matrix Service"

    private var glyphMatrixManager: GlyphMatrixManager? = null
    private var glyphMatrixManagerCallback: GlyphMatrixManager.Callback? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var clearRunnable: Runnable? = null

    private var initialized = false
    private val timeout = 10000L

    inline fun <reified T : Parcelable> Intent?.parcelableExtra(name: String): T? {
        if (this == null) return null
        return getParcelableExtra(name, T::class.java)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        Log.d(tag, "onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            clearRunnable?.let { mainHandler.removeCallbacks(it) }
            clearRunnable = null

            val glyph = intent?.parcelableExtra<Bitmap>(Constants.GLYPH_EXTRA_BITMAP)

            if (initialized) onGlyph(glyph)
            else onInit(glyph)
        } catch (e: Exception) {
            Log.e(tag, "Failed to start service: $e")
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.d(tag, "onDestroy")

        initialized = false
    }

    private fun onInit(glyph: Bitmap?) {
        if (initialized) return

        Log.d(tag, "onInit")

        glyphMatrixManager = GlyphMatrixManager.getInstance(this)
        glyphMatrixManagerCallback = object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(componentName: ComponentName?) {
                Log.d(tag, "Connected: $componentName")

                try {
                    glyphMatrixManager?.register(Glyph.DEVICE_23112)
                    initialized = true

                    Log.d(tag, "Initialized")

                    onGlyph(glyph)
                } catch (e: Exception) {
                    Log.e(tag, "Failed initialization: $e")
                }
            }

            override fun onServiceDisconnected(componentName: ComponentName?) {
                Log.d(tag, "Disconnected: $componentName")
                initialized = false
            }
        }

        glyphMatrixManager?.init(glyphMatrixManagerCallback)
    }

    private fun onGlyph(glyph: Bitmap?) {
        Log.d(tag, "onGlyph")

        if (glyph == null) return

        showSimple(glyph)
    }

    private fun showSimple(glyph: Bitmap) {
        try {
            val objBuilder = GlyphMatrixObject.Builder()
            val image = objBuilder
                .setImageSource(glyph)
                .setScale(100)
                .setOrientation(0)
                .setPosition(0, 0)
                .setReverse(false)
                .build()

            val frameBuilder = GlyphMatrixFrame.Builder()
            val frame = frameBuilder.addTop(image).build(this)
            val rendered = frame.render()

            glyphMatrixManager?.setAppMatrixFrame(rendered)
        } catch (e: Exception) {
            Log.e(tag, "Failed to show glyph: $e")
        }

        val runnable = Runnable {
            try {
                glyphMatrixManager?.closeAppMatrix()
            } catch (e: Exception) {
                Log.e(tag, "Failed to close glyph matrix: $e")
            } finally {
                clearRunnable = null
            }
        }

        clearRunnable = runnable
        mainHandler.postDelayed(runnable, timeout)
    }
}
