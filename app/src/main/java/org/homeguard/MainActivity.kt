package org.homeguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Re-checked by the UI on resume; nothing to do on the callback itself. */ }

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
                        },
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestNotificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            }
                        },
                        onOpenNotificationSettings = {
                            openAppNotificationSettings()
                        }
                    )
                }
            }
        }
    }

    /**
     * Opens this app's notification settings.
     *
     * Needed because once POST_NOTIFICATIONS is permanently denied the runtime request does nothing,
     * and the only remaining route is the settings screen.
     */
    private fun openAppNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
        }
        // Guarded because some OEM builds do not resolve the action; a crash here would be far
        // worse than leaving the user on the current screen.
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, R.string.push_gate_settings_unavailable, Toast.LENGTH_LONG).show()
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