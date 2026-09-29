package org.homeguard.core.hardware

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
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
                if (cameraId == null && mgr.cameraIdList.isNotEmpty()) {
                    cameraId = mgr.cameraIdList[0]
                }
                mgr.registerTorchCallback(torchCallback, null)
            }
        } catch (e: Exception) {
            Log.e("TorchManager", "Error initializing flashlight: ${e.message}")
        }
    }

    fun toggleTorch() {
        setTorch(!_isTorchOn.value)
    }

    fun setTorch(enabled: Boolean) {
        if (!hasFlash || cameraId == null || cameraManager == null) {
            Log.w("TorchManager", "Torch unavailable on this hardware.")
            return
        }
        try {
            cameraManager.setTorchMode(cameraId!!, enabled)
            _isTorchOn.value = enabled
        } catch (e: CameraAccessException) {
            Log.e("TorchManager", "Camera access exception toggling torch: ${e.message}")
        } catch (e: Exception) {
            Log.e("TorchManager", "Unexpected exception toggling torch: ${e.message}")
        }
    }

    fun cleanUp() {
        setTorch(false)
        try {
            cameraManager?.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) {}
    }
}
