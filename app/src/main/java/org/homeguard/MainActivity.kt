package org.homeguard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import org.homeguard.core.designsystem.theme.HomeGuardTheme
import org.homeguard.ui.HomeGuardApp
import org.homeguard.ui.HomeViewModel

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    /**
     * P0-6: the microphone is requested only in response to an explicit user action that turns on
     * acoustic detection. It is deliberately not requested from onCreate or onResume.
     */
    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            homeViewModel.onAcousticPermissionGranted()
        } else {
            homeViewModel.onAcousticPermissionDenied()
        }
    }

    private val requestTorchPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            homeViewModel.toggleFlashlight()
        } else {
            homeViewModel.onTorchPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HomeGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeGuardApp(
                        viewModel = homeViewModel,
                        onRequestAcousticPermission = {
                            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onRequestTorchPermission = {
                            requestTorchPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    )
                }
            }
        }
    }

    /** Entry point for the acoustic-detection toggle in settings. */
    fun requestAcousticDetection() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            homeViewModel.onAcousticPermissionGranted()
        } else {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}