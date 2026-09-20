package pl.quicktask.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.PackageInfoCompat
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

internal class RequiredUpdateGate(private val activity: ComponentActivity) {
    private val manager = AppUpdateManagerFactory.create(activity)
    private val preferences = activity.getSharedPreferences("required_update", Context.MODE_PRIVATE)
    private val installedVersion = PackageInfoCompat.getLongVersionCode(
        activity.packageManager.getPackageInfo(activity.packageName, 0),
    )
    private val isDebug = activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private var requiredVersion = preferences.getLong("version_code", 0)
    private val handler = Handler(Looper.getMainLooper())
    var checking by mutableStateOf(false)
        private set
    private var disposed = false
    private var flowRunning = false
    private var automaticallyAttempted = false
    private var requestId = 0
    var state by mutableStateOf(if (isDebug) UpdateGateState.Ready else updateGateState(installedVersion, requiredVersion))
        private set
    var storeUnavailable by mutableStateOf(false)
        private set

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) {
        flowRunning = false
        // Neither cancellation nor RESULT_OK proves that the new APK is installed.
        // Google Play restarts the process after an immediate update is installed.
        state = updateGateState(installedVersion, requiredVersion)
    }

    fun check(userRequested: Boolean = false) {
        if (isDebug || disposed || flowRunning) return
        if (checking && !userRequested) return
        checking = true
        storeUnavailable = false
        val id = ++requestId
        val timeout = Runnable {
            if (!disposed && id == requestId && checking) {
                checking = false
                ++requestId // Ignore a late response from this request.
                state = updateGateState(installedVersion, requiredVersion)
            }
        }
        handler.postDelayed(timeout, 15_000)
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (disposed || id != requestId) return@addOnSuccessListener
                handler.removeCallbacks(timeout)
                checking = false
                val inProgress = info.updateAvailability() ==
                    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE || inProgress) {
                    requiredVersion = maxOf(requiredVersion, info.availableVersionCode().toLong())
                    preferences.edit().putLong("version_code", requiredVersion).apply()
                    state = UpdateGateState.Required
                    if (userRequested || !automaticallyAttempted || inProgress) {
                        automaticallyAttempted = true
                        if (info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) || inProgress) {
                            startUpdate(info)
                        } else if (userRequested) {
                            openStore()
                        }
                    }
                } else {
                    state = updateGateState(installedVersion, requiredVersion)
                    if (userRequested && state == UpdateGateState.Required) openStore()
                }
            }
            .addOnFailureListener {
                if (disposed || id != requestId) return@addOnFailureListener
                handler.removeCallbacks(timeout)
                checking = false
                state = updateGateState(installedVersion, requiredVersion)
                if (userRequested && state == UpdateGateState.Required) openStore()
            }
    }

    private fun startUpdate(info: AppUpdateInfo) {
        flowRunning = try {
            manager.startUpdateFlowForResult(
                info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
            )
        } catch (_: Exception) {
            false
        }
        // Keep the blocking screen visible if Google Play cannot start its flow.
    }

    fun openStore() {
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${activity.packageName}"))
            .setPackage("com.android.vending")
        try {
            activity.startActivity(marketIntent)
        } catch (_: ActivityNotFoundException) {
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=${activity.packageName}")))
            } catch (_: ActivityNotFoundException) {
                storeUnavailable = true
            }
        }
    }

    fun dispose() {
        disposed = true
        handler.removeCallbacksAndMessages(null)
    }
}
