package org.homeguard.core.hardware

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TorchManager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val hasFlash = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private var cameraId: String? = null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(camId: String, enabled: Boolean) {
            if (camId == cameraId) {
                _isTorchOn.value = enabled
            }
        }

        override fun onTorchModeUnavailable(camId: String) {
            if (camId == cameraId) {
                _isTorchOn.value = false
            }
        }
    }

    init {
        try {
            cameraManager?.let { mgr ->
                for (id in mgr.cameraIdList) {
                    val characteristics = mgr.getCameraCharacteristics(id)
                    val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (flashAvailable && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        cameraId = id
                        break
                    }
                }
                // No fallback to cameraIdList[0]: if no back-facing flash exists the torch is
                // genuinely unavailable, and pretending otherwise produced a dead toggle.
                mgr.registerTorchCallback(torchCallback, null)
            }
        } catch (e: Exception) {
            Log.e("TorchManager", "Error initializing flashlight: ${e.message}")
        }
    }

    val isAvailable: Boolean get() = hasFlash && cameraId != null && cameraManager != null

    /** True when the caller still needs to request the runtime CAMERA permission. */
    fun needsCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED

    fun toggleTorch() {
        setTorch(!_isTorchOn.value)
    }

    fun setTorch(enabled: Boolean) {
        if (!isAvailable) {
            Log.w("TorchManager", "Torch unavailable on this hardware.")
            return
        }
        if (needsCameraPermission()) {
            Log.w("TorchManager", "CAMERA permission not granted; cannot control torch.")
            return
        }
        val id = cameraId ?: return
        try {
            cameraManager?.setTorchMode(id, enabled)
            _isTorchOn.value = enabled
        } catch (e: CameraAccessException) {
            Log.e("TorchManager", "Camera access exception toggling torch: ${e.message}")
            _isTorchOn.value = false
        } catch (e: SecurityException) {
            Log.e("TorchManager", "Missing camera permission toggling torch: ${e.message}")
            _isTorchOn.value = false
        } catch (e: Exception) {
            Log.e("TorchManager", "Unexpected exception toggling torch: ${e.message}")
            _isTorchOn.value = false
        }
    }

    fun cleanUp() {
        setTorch(false)
        try {
            cameraManager?.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) {}
    }
}
