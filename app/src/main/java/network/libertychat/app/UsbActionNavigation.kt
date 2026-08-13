package network.columba.app

/**
 * Whether a USB-device-action screen should be dismissed because the device it
 * was showing has been detached. Kept after the firmware-flasher/Pyxis removal:
 * the Pyxis firmware classifier that used to share this file is gone, but this
 * navigation helper is still used by MainActivity.
 */
internal fun shouldDismissUsbAction(
    route: String?,
    activeDeviceId: Int?,
    detachedDeviceId: Int,
): Boolean = route?.startsWith("usb_device_action") == true && activeDeviceId == detachedDeviceId
