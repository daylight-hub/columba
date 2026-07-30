/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. */

package network.columba.app.rns.backend.py

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import network.columba.app.rns.api.model.InterfaceConfig
import network.columba.app.rns.api.model.ReticulumConfig
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit

/**
 * LCS: resolve `.local` (mDNS) hostnames to numeric IPs before the host string
 * is handed to Python RNS.
 *
 * Why this is needed: on Android there is no system mDNS resolver in the C
 * library, so Python's `socket.create_connection()` (and the JVM's
 * `InetAddress`) cannot resolve `iprnode.local`. Android only does mDNS through
 * [NsdManager]. We bridge by resolving the name in Kotlin and passing Python a
 * numeric IP, which it can connect to normally.
 *
 * Resolution runs at RNS start (see [PythonRnsRuntime.start]), so it re-resolves
 * every time the interface comes up — an RNode whose DHCP lease changed is
 * picked up on the next (re)connect without the user editing anything.
 *
 * Best-effort: on timeout or failure the original `.local` host is passed
 * through unchanged. Python then fails to connect exactly as it does today, but
 * the log line explains why. We never block startup indefinitely.
 */
internal object MdnsResolver {
    private const val TAG = "MdnsResolver"
    private const val RESOLVE_TIMEOUT_MS = 4000L

    /**
     * Return a copy of [config] with every `.local` TCPClient targetHost
     * resolved to a numeric IP where possible.
     */
    fun resolveInterfaces(context: Context, config: ReticulumConfig): ReticulumConfig {
        if (config.enabledInterfaces.none { it is InterfaceConfig.TCPClient && isDotLocal(it.targetHost) }) {
            return config
        }
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: run {
                Log.w(TAG, "NsdManager unavailable; leaving .local hosts unresolved")
                return config
            }

        val resolved = config.enabledInterfaces.map { iface ->
            if (iface is InterfaceConfig.TCPClient && isDotLocal(iface.targetHost)) {
                val ip = resolveHost(nsd, iface.targetHost)
                if (ip != null) {
                    Log.i(TAG, "Resolved ${iface.targetHost} -> $ip")
                    iface.copy(targetHost = ip)
                } else {
                    Log.w(TAG, "Could not resolve ${iface.targetHost}; passing through unchanged")
                    iface
                }
            } else {
                iface
            }
        }
        return config.copy(enabledInterfaces = resolved)
    }

    private fun isDotLocal(host: String): Boolean =
        host.trim().lowercase().removeSuffix(".").endsWith(".local")

    /**
     * Resolve a single "<name>.local" via NsdManager. NsdManager resolves
     * *service instances*, not bare hostnames, so we register the host as an
     * instance under the standard _workstation._tcp / generic type and read the
     * resolved address. On API 34+ the newer resolver callback is used; below
     * that, the deprecated resolveService path.
     *
     * Returns a numeric IP string, or null on timeout/failure.
     */
    @SuppressLint("NewApi")
    private fun resolveHost(nsd: NsdManager, host: String): String? {
        // Strip the trailing ".local" (and any dot) to get the instance label.
        val instance = host.trim().removeSuffix(".").removeSuffix(".local")
        val latch = CountDownLatch(1)
        val address = AtomicReference<String?>(null)

        val info = NsdServiceInfo().apply {
            serviceName = instance
            serviceType = "_workstation._tcp."
        }

        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.d(TAG, "resolve failed for $host (err=$errorCode)")
                latch.countDown()
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                address.set(pickAddress(serviceInfo))
                latch.countDown()
            }
        }

        return try {
            @Suppress("DEPRECATION")
            nsd.resolveService(info, listener)
            if (!latch.await(RESOLVE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.d(TAG, "resolve timed out for $host")
                null
            } else {
                address.get()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "resolve threw for $host", t)
            null
        }
    }

    private fun pickAddress(info: NsdServiceInfo?): String? {
        if (info == null) return null
        // API 34+ exposes hostAddresses (List<InetAddress>); older uses host.
        val addrs: List<InetAddress> =
            if (Build.VERSION.SDK_INT >= 34) {
                @Suppress("DEPRECATION")
                info.hostAddresses.ifEmpty { info.host?.let { listOf(it) } ?: emptyList() }
            } else {
                @Suppress("DEPRECATION")
                info.host?.let { listOf(it) } ?: emptyList()
            }
        // Prefer IPv4 for RNode TCP.
        return (addrs.firstOrNull { it.address?.size == 4 } ?: addrs.firstOrNull())
            ?.hostAddress
    }
}
