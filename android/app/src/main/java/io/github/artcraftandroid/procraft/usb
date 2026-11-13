package io.github.artcraftandroid.procraft.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import android.widget.Toast

/** First-stage Huion USB bridge. It deliberately does not change tablet state yet. */
class HuionUsbController(private val context: Context) {
    companion object {
        private const val TAG = "ProCraft-Huion"
        private const val ACTION_PERMISSION = "io.github.artcraftandroid.procraft.HUION_USB_PERMISSION"
        private const val HUION_VID = 0x256c
        private const val HS610_PID = 0x006d
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var connection: UsbDeviceConnection? = null
    private var claimedInterface: UsbInterface? = null
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != ACTION_PERMISSION) return
            val device = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            if (device != null && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                open(device)
            } else if (device != null) {
                Log.w(TAG, "USB permission denied: ${device.deviceName}")
            }
        }
    }

    fun start() {
        if (!registered) {
            val filter = IntentFilter(ACTION_PERMISSION)
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            registered = true
        }

        usbManager.deviceList.values.firstOrNull(::isHs610)?.let { device ->
            if (usbManager.hasPermission(device)) open(device)
            else requestPermission(device)
        }
    }

    fun stop() {
        claimedInterface?.let { connection?.releaseInterface(it) }
        claimedInterface = null
        connection?.close()
        connection = null
        if (registered) {
            context.unregisterReceiver(receiver)
            registered = false
        }
    }

    private fun isHs610(device: UsbDevice): Boolean =
        device.vendorId == HUION_VID && device.productId == HS610_PID

    private fun requestPermission(device: UsbDevice) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(context, 0, Intent(ACTION_PERMISSION).setPackage(context.packageName), flags)
        Log.i(TAG, "HS610 detected: requesting USB permission")
        usbManager.requestPermission(device, pi)
    }

    private fun open(device: UsbDevice) {
        stopConnectionOnly()
        val candidate = (0 until device.interfaceCount)
            .map { device.getInterface(it) }
            .firstOrNull { intf ->
                intf.interfaceClass == UsbConstants.USB_CLASS_HID || intf.endpointCount > 0
            } ?: run {
                Log.w(TAG, "HS610 has no usable USB interface")
                return
            }

        val conn = usbManager.openDevice(device) ?: run {
            Log.e(TAG, "Could not open HS610")
            return
        }
        if (!conn.claimInterface(candidate, true)) {
            Log.e(TAG, "Could not claim HS610 interface ${candidate.id}")
            conn.close()
            return
        }

        connection = conn
        claimedInterface = candidate
        Log.i(TAG, "HS610 USB opened: VID=%04x PID=%04x interface=%d endpoints=%d".format(device.vendorId, device.productId, candidate.id, candidate.endpointCount))
        Toast.makeText(context, "HS610 USB détectée — ProCraft", Toast.LENGTH_SHORT).show()
    }

    private fun stopConnectionOnly() {
        claimedInterface?.let { connection?.releaseInterface(it) }
        claimedInterface = null
        connection?.close()
        connection = null
    }
}
