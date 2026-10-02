package org.homeguard.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Torch control requires a runtime CAMERA grant on API 23+. */
fun torchPermissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED