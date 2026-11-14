
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
import androidx.core.content.ContextCompat

/** First-stage Huion USB bridge for HC16. */
class HuionUsbController(private val context: Context) {

    companion object {
        private const val TAG = "ProCraft-Huion"
        private const val ACTION_PERMISSION =
            "io.github.artcraftandroid.procraft.HUION_USB_PERMISSION"

        private const val HUION_VID = 0x3002
        private const val HC16_PID = 0x0064
    }

    private val usbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var connection: UsbDeviceConnection? = null
    private var claimedInterface: UsbInterface? = null
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != ACTION_PERMISSION) return

            val device = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(
                    UsbManager.EXTRA_DEVICE,
                    UsbDevice::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }

            if (device != null &&
                intent.getBooleanExtra(
                    UsbManager.EXTRA_PERMISSION_GRANTED,
                    false
                )
            ) {
                Log.i(TAG, "HC16 USB permission granted")
                open(device)
            } else if (device != null) {
                Log.w(TAG, "HC16 USB permission denied")
                Toast.makeText(
                    context,
                    "Autorisation USB refusée",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun start() {
    if (!registered) {
        val filter = IntentFilter(ACTION_PERMISSION)

        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(
                receiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        registered = true
    }

    val devices = usbManager.deviceList.values

    Log.i(TAG, "USB devices found: ${devices.size}")

    devices.forEach { device ->
        Log.i(
            TAG,
            "USB device: name=${device.deviceName}, " +
                "manufacturer=${device.manufacturerName}, " +
                "product=${device.productName}, " +
                "VID=%04X PID=%04X".format(
                    device.vendorId,
                    device.productId
                )
        )
    }

    val device = devices.firstOrNull(::isHC16)

    if (device != null) {
        Toast.makeText(
            context,
            "HC16 trouvée : VID=%04X PID=%04X"
                .format(device.vendorId, device.productId),
            Toast.LENGTH_LONG
        ).show()

        if (usbManager.hasPermission(device)) {
            Toast.makeText(
                context,
                "Autorisation HC16 déjà accordée",
                Toast.LENGTH_SHORT
            ).show()

            open(device)
        } else {
            requestPermission(device)
        }
    } else {
    val details = devices.joinToString(" | ") { device ->
        "VID=%04X PID=%04X".format(
            device.vendorId,
            device.productId
        )
    }

    val message = if (devices.isEmpty()) {
        "Aucun périphérique USB visible par ProCraft"
    } else {
        "USB trouvés (${devices.size}) : $details"
    }

    Log.w(TAG, message)

    Toast.makeText(
        context,
        message,
        Toast.LENGTH_LONG
    ).show()
    }
    }

    fun stop() {
        stopConnectionOnly()

        if (registered) {
            context.unregisterReceiver(receiver)
            registered = false
        }
    }

    private fun isHC16(device: UsbDevice): Boolean {
        return device.vendorId == HUION_VID &&
            device.productId == HC16_PID
    }

    private fun requestPermission(device: UsbDevice) {
        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE

        val pi = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_PERMISSION).setPackage(context.packageName),
            flags
        )

        Log.i(TAG, "Requesting HC16 USB permission")

        Toast.makeText(
            context,
            "Huion HC16 détectée — demande d'autorisation",
            Toast.LENGTH_SHORT
        ).show()

        usbManager.requestPermission(device, pi)
    }

    private fun open(device: UsbDevice) {
        stopConnectionOnly()

        val candidate = (0 until device.interfaceCount)
            .map { device.getInterface(it) }
            .firstOrNull { intf ->
                intf.interfaceClass == UsbConstants.USB_CLASS_HID ||
                    intf.endpointCount > 0
            } ?: run {
                Log.w(TAG, "HC16 has no usable USB interface")
                Toast.makeText(
                    context,
                    "HC16 détectée, interface USB introuvable",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

        val conn = usbManager.openDevice(device) ?: run {
            Log.e(TAG, "Could not open HC16")
            Toast.makeText(
                context,
                "Impossible d'ouvrir la HC16",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (!conn.claimInterface(candidate, true)) {
            Log.e(TAG, "Could not claim HC16 interface ${candidate.id}")
            conn.close()
            Toast.makeText(
                context,
                "Impossible d'accéder à l'interface HC16",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        connection = conn
        claimedInterface = candidate

        Log.i(
            TAG,
            "HC16 USB opened: VID=%04X PID=%04X interface=%d endpoints=%d"
                .format(
                    device.vendorId,
                    device.productId,
                    candidate.id,
                    candidate.endpointCount
                )
        )

        Toast.makeText(
            context,
            "Huion HC16 détectée et connectée à ProCraft",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun stopConnectionOnly() {
        claimedInterface?.let {
            connection?.releaseInterface(it)
        }

        claimedInterface = null
        connection?.close()
        connection = null
    }
}
