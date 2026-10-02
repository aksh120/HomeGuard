package org.homeguard

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
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
     * CAMERA is requested only in response to an explicit flashlight toggle. The master
     * specification excludes microphone access, so there is no audio permission flow here.
     */
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

        handleCheckInIntent(intent)

        setContent {
            HomeGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeGuardApp(
                        viewModel = homeViewModel,
                        onRequestTorchPermission = {
                            requestTorchPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCheckInIntent(intent)
    }

    /** Opens the safety-check prompt when launched from the scheduled-check notification. */
    private fun handleCheckInIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_CHECK_IN_PROMPT, false) == true) {
            homeViewModel.onCheckInNotificationOpened()
        }
    }

    companion object {
        const val EXTRA_OPEN_CHECK_IN_PROMPT = "org.homeguard.extra.OPEN_CHECK_IN_PROMPT"
    }
}