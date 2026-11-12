package de.ltsdesign.ltscontrol.bluetooth

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.IntentCompat

private const val TAG = "Pairing"

/**
 * Pairs the Respooler through the system's companion device dialog (the Android counterpart to
 * AccessorySetupKit on iOS). The system scans, so the app needs no scan or location permission.
 */
class DevicePairing(context: Context) {
    private val manager = context.applicationContext.getSystemService(CompanionDeviceManager::class.java)

    /** Asks the system to look for a board; [onChooser] receives the dialog to launch. */
    fun findDevice(onChooser: (IntentSender) -> Unit, onFailure: (CharSequence?) -> Unit) {
        val filter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(ScanFilter.Builder().setServiceUuid(ParcelUuid(BleManager.serviceUuid)).build())
            .build()
        val request = AssociationRequest.Builder()
            .addDeviceFilter(filter)
            .setSingleDevice(false)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.associate(request, Runnable::run, object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) = onChooser(intentSender)
                override fun onAssociationCreated(associationInfo: AssociationInfo) = Unit
                override fun onFailure(error: CharSequence?) = onFailure(error)
            })
        } else {
            @Suppress("DEPRECATION")
            manager.associate(request, object : CompanionDeviceManager.Callback() {
                @Deprecated("Used below Android 13")
                override fun onDeviceFound(intentSender: IntentSender) = onChooser(intentSender)
                override fun onFailure(error: CharSequence?) = onFailure(error)
            }, null)
        }
    }

    /** The Bluetooth address of the board chosen in the dialog. */
    fun addressFromResult(data: Intent?): String? {
        data ?: return null
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            IntentCompat.getParcelableExtra(data, CompanionDeviceManager.EXTRA_ASSOCIATION, AssociationInfo::class.java)
                ?.deviceMacAddress?.toString()
        } else {
            @Suppress("DEPRECATION")
            IntentCompat.getParcelableExtra(data, CompanionDeviceManager.EXTRA_DEVICE, ScanResult::class.java)
                ?.device?.address
        }
        Log.i(TAG, "Chosen device $address")
        return address?.uppercase()
    }

    /** Removes the association, so the board shows up in the dialog again. */
    fun forget(address: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.myAssociations
                .filter { it.deviceMacAddress?.toString().equals(address, ignoreCase = true) }
                .forEach { manager.disassociate(it.id) }
        } else {
            @Suppress("DEPRECATION")
            runCatching { manager.disassociate(address) }
        }
    }
}
