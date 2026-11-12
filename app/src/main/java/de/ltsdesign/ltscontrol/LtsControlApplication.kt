package de.ltsdesign.ltscontrol

import android.app.Application
import de.ltsdesign.ltscontrol.bluetooth.BleManager
import de.ltsdesign.ltscontrol.bluetooth.DevicePairing
import de.ltsdesign.ltscontrol.data.AppPreferences
import de.ltsdesign.ltscontrol.data.FirmwareUpdateRepository
import de.ltsdesign.ltscontrol.notifications.RespoolNotifications

/** Holds the connection for the whole process, so it survives activity recreation. */
class LtsControlApplication : Application() {
    val preferences by lazy { AppPreferences(this) }
    val notifications by lazy { RespoolNotifications(this, preferences) }
    val bleManager by lazy { BleManager(this, notifications) }
    val pairing by lazy { DevicePairing(this) }
    val firmwareUpdates by lazy { FirmwareUpdateRepository(this) }
}
