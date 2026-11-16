package com.meticulouscreations.homesafe.data

import android.content.Context
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.meticulouscreations.homesafe.ActivityAccess
import com.meticulouscreations.homesafe.PlatformContext
import com.meticulouscreations.homesafe.domain.platform.IdentityCheck
import com.meticulouscreations.homesafe.shared.R
import homesafe.shared.generated.resources.Res
import homesafe.shared.generated.resources.biometric_name_fingerprint
import homesafe.shared.generated.resources.biometric_name_generic
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.StringResource
import kotlin.coroutines.resume

/**
 * A plain `BiometricPrompt`, with no key behind it: the answer is all that is wanted. The same
 * class of biometric the saved login asks for, so a device that can do one can do the other.
 */
private class AndroidIdentityCheck(private val context: Context, private val activities: ActivityAccess) : IdentityCheck {

    override val isAvailable: Boolean
        get() = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    // A strong biometric can be a face. Only a phone with no face or iris unlock at all is
    // certain to be asking for a finger; any other is asked for "biometrics".
    override val methodName: StringResource = context.packageManager.let { features ->
        val fingerprintOnly = features.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) &&
            !features.hasSystemFeature(PackageManager.FEATURE_FACE) &&
            !features.hasSystemFeature(PackageManager.FEATURE_IRIS)
        if (fingerprintOnly) Res.string.biometric_name_fingerprint else Res.string.biometric_name_generic
    }

    override suspend fun confirm(): Result<Unit> {
        // The window that is up now: the graph outlives the Activity it was made in.
        val host = activities.current ?: return Result.failure(BiometricAuthException("No Activity is up to show a prompt from"))
        return prompt(host)
    }

    private suspend fun prompt(host: FragmentActivity): Result<Unit> = suspendCancellableCoroutine { continuation ->
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (continuation.isActive) continuation.resume(Result.success(Unit))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (continuation.isActive) continuation.resume(Result.failure(BiometricAuthException(errString.toString())))
            }

            // One attempt that wasn't recognised; the prompt stays up for another. Only an error ends it.
            override fun onAuthenticationFailed() = Unit
        }
        val prompt = BiometricPrompt(host, ContextCompat.getMainExecutor(host), callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.biometric_finance_title))
            .setSubtitle(context.getString(R.string.biometric_finance_subtitle))
            .setNegativeButtonText(context.getString(R.string.biometric_cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        prompt.authenticate(promptInfo)
    }
}

/** A graph built with no Activity (a background receiver) has nobody to show a prompt to. */
actual fun createIdentityCheck(context: PlatformContext): IdentityCheck =
    context.activities?.let { AndroidIdentityCheck(context.context.applicationContext, it) } ?: NoIdentityCheck
