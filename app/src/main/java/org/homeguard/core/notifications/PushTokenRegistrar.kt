package org.homeguard.core.notifications

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.homeguard.HomeGuardApplication
import org.homeguard.core.di.AppContainer

/**
 * Keeps the backend informed of this install's FCM registration token.
 *
 * Both roles register. A caregiver needs it to receive alerts; Phase 5's remote alarm needs the
 * resident's phone to be reachable too, so restricting registration to the caregiver profile would
 * bake in a limitation that does not exist.
 *
 * Every failure path is non-fatal. Push not working is serious, but crashing the app on launch
 * because Firebase is unconfigured would be worse, and would also hide the reason.
 */
object PushTokenRegistrar {

    private const val TAG = "HomeGuardPush"

    /**
     * Fetches the current token and stores it.
     *
     * Safe to call repeatedly: the token column is globally unique and upserted, so a re-registration
     * after an FCM rotation replaces the row instead of creating a duplicate.
     */
    suspend fun register(context: Context) {
        if (!isFirebaseConfigured(context)) {
            Log.i(TAG, "Firebase is not configured; skipping push token registration")
            return
        }
        runCatching {
            FirebaseMessaging.getInstance().token.await()
        }.onSuccess { token ->
            if (token.isBlank()) {
                Log.w(TAG, "FCM returned a blank token")
                return
            }
            register(context, token)
        }.onFailure { Log.w(TAG, "Could not obtain an FCM token", it) }
    }

    /** Stores [token] for this install. Called again by onNewToken when FCM rotates it. */
    suspend fun register(context: Context, token: String) {
        val app = context.applicationContext as? HomeGuardApplication ?: return
        val container: AppContainer = app.container
        container.remoteSyncDataSource.registerPushToken(token)
            .onSuccess { Log.i(TAG, "Push token registered") }
            .onFailure {
                // Not fatal. The next launch, or the next token rotation, retries.
                Log.w(TAG, "Could not register push token; alerts may not arrive", it)
            }
    }

    /**
     * True when Firebase has actually initialised.
     *
     * Checked rather than assumed, because the google-services plugin is applied only when its
     * config file exists. Touching the Firebase SDK without it throws, and that must not happen on
     * a developer's first run.
     */
    fun isFirebaseConfigured(context: Context): Boolean =
        runCatching {
            com.google.firebase.FirebaseApp.getApps(context).isNotEmpty()
        }.getOrDefault(false)
}

/**
 * Bridges the Task-based Firebase token API to a coroutine.
 *
 * Hand written rather than pulling in an extra await library: this is the only Task in the codebase,
 * and a dependency for one call site would be bloat.
 */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { error ->
            if (cont.isActive) cont.resumeWithException(error)
        }
        addOnCanceledListener {
            cont.cancel()
        }
    }
