package org.example.moxia.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast
import org.example.moxia.R

/**
 * Receives the outcome of a [PackageInstaller] session opened by [UpdateInstaller].
 *
 * `STATUS_PENDING_USER_ACTION` is the normal path: the system asks for confirmation and the
 * receiver is what launches that page. Failing to launch it silently is how "I tapped install and
 * nothing happened" happens, so that failure is logged instead of swallowed.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = intent.confirmationIntent()
                if (confirmation == null) {
                    Log.w(TAG, "Install confirmation intent missing")
                    return
                }
                // The receiver has no activity task of its own; the page needs its own.
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(confirmation)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not open the install confirmation page", e)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> Log.i(TAG, "Update installed")

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Toast.makeText(
                    context,
                    message ?: context.getString(R.string.update_install_failed_generic),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun Intent.confirmationIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_INTENT)
        }

    private companion object {
        const val TAG = "InstallResultReceiver"
    }
}
