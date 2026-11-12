package org.example.moxia.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

/** The three ways handing a package to the system installer can end. */
sealed interface InstallOutcome {
    /** The session was committed; the system confirmation page follows, success is not known yet. */
    data object Submitted : InstallOutcome

    /** The user has to allow "install unknown apps" for this app first. */
    data object NeedsPermission : InstallOutcome

    data class Failed(val reason: String) : InstallOutcome
}

/**
 * Installs through [PackageInstaller] rather than an `ACTION_VIEW` intent.
 *
 * The session API keeps the package inside this app's own install session: no file manager is
 * needed, no FileProvider, and the result comes back as a broadcast so a failure can be explained.
 * Two constraints are easy to break and both look like "the button does nothing":
 * `REQUEST_INSTALL_PACKAGES` must be declared, and the status PendingIntent must be mutable so the
 * system can attach the result extras.
 */
class UpdateInstaller(private val context: Context) {

    fun install(apk: File): InstallOutcome {
        if (!apk.isFile || apk.length() <= 0L) {
            return InstallOutcome.Failed("Package is missing")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            return InstallOutcome.NeedsPermission
        }
        return try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(context.packageName)
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite(APK_NAME, 0L, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                // targetSdk 35+: the system writes EXTRA_STATUS into the callback intent, which
                // an immutable PendingIntent rejects outright.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val statusIntent = Intent(context, InstallResultReceiver::class.java)
                    .setAction(ACTION_INSTALL_STATUS)
                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, statusIntent, flags)
                session.commit(pendingIntent.intentSender)
            }
            InstallOutcome.Submitted
        } catch (e: Exception) {
            InstallOutcome.Failed(e.message ?: "Installer rejected the session")
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "org.example.moxia.action.INSTALL_STATUS"
        private const val APK_NAME = "base.apk"
    }
}
