package com.agentos.app.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.agentos.app.core.logging.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * Coordinates the real MediaProjection screen-capture consent flow.
 *
 * Because MediaProjection requires the user to accept a system dialog per
 * session, [requestScreenshot] must be called while MainActivity is resumed.
 * The activity observes [uiRequest] and launches the system consent dialog;
 * results come back through [onConsentResult]. The capture itself is real:
 * a virtual display renders one frame into an ImageReader and is saved as PNG.
 */
object ScreenCaptureCoordinator {

    private val _uiRequest = MutableStateFlow(false)
    val uiRequest: StateFlow<Boolean> = _uiRequest.asStateFlow()

    private val pendingConsent = AtomicReference<CompletableDeferred<Pair<Int, Intent>?>>(null)

    suspend fun requestScreenshot(context: Context): String? {
        val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        Logger.i("ScreenCap", "Requesting MediaProjection consent")

        // 1. Ask the Activity (if visible) to launch the consent dialog
        val consent = CompletableDeferred<Pair<Int, Intent>?>()
        pendingConsent.set(consent)
        _uiRequest.value = true
        val result = withTimeoutOrNull(45_000L) { consent.await() }
        _uiRequest.value = false
        pendingConsent.set(null)
        if (result == null) {
            Logger.w("ScreenCap", "Consent flow timed out or was not handled (was the app in foreground?)")
            return null
        }
        val (resultCode, data) = result

        // 2. Real capture through a foreground service-backed projection
        return withContext(Dispatchers.IO) {
            captureOnce(context, resultCode, data)
        }
    }

    /** MainActivity calls this with the ActivityResult it receives. */
    fun onConsentResult(resultCode: Int, data: Intent?) {
        val deferred = pendingConsent.get() ?: return
        if (data != null && resultCode == android.app.Activity.RESULT_OK) {
            deferred.complete(resultCode to data)
        } else {
            deferred.complete(null)
        }
    }

    private suspend fun captureOnce(context: Context, resultCode: Int, data: Intent): String? {
        val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = try {
            mpManager.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            Logger.e("ScreenCap", "getMediaProjection failed", e)
            return null
        }
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { Logger.i("ScreenCap", "MediaProjection stopped") }
        }, Handler(Looper.getMainLooper()))

        return try {
            val metrics = context.resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val dpi = metrics.densityDpi

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            val imageDeferred = CompletableDeferred<Image>()
            reader.setOnImageAvailableListener({ r ->
                val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                if (!imageDeferred.isCompleted) imageDeferred.complete(img)
            }, Handler(Looper.getMainLooper()))

            val vDisplay = projection.createVirtualDisplay(
                "AgentOSCapture", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, null
            )

            val image = withTimeoutOrNull(8_000L) { imageDeferred.await() }
            if (image == null) {
                Logger.w("ScreenCap", "No frame captured within timeout")
                return null
            }

            val bitmap = image.toBitmap(width, height)
            image.close()

            val dir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES) ?: context.filesDir, "screenshots")
            dir.mkdirs()
            val file = File(dir, "screenshot_${System.currentTimeMillis()}.png")
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 90, out) }
            bitmap.recycle()
            Logger.i("ScreenCap", "Screenshot saved: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Logger.e("ScreenCap", "captureOnce failed", e)
            null
        } finally {
            runCatching { projection.stop() }
        }
    }

    private fun Image.toBitmap(width: Int, height: Int): Bitmap {
        val plane = planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val rowPadding = rowStride - pixelStride * width
        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return if (rowPadding == 0) bitmap
        else Bitmap.createBitmap(bitmap, 0, 0, width, height)
    }
}
