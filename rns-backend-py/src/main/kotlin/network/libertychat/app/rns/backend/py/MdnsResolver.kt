/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. */

package network.libertychat.app.rns.backend.py

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import network.libertychat.app.rns.api.model.InterfaceConfig
import network.libertychat.app.rns.api.model.ReticulumConfig
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * LCS: resolve `.local` (mDNS) hostnames to numeric IPs before the host string
 * is handed to Python RNS.
 *
 * Android has no system mDNS resolver in its C library, so Python's
 * `socket.create_connection()` and the JVM's `InetAddress` cannot resolve
 * `iprnode.local`. Only [NsdManager] can. We bridge by resolving in Kotlin and
 * passing Python a numeric IP.
 *
 * RNodes advertise the `_reticulum._tcp` service type (confirmed via
 * `dns-sd -B`). NsdManager resolves *service instances*, not bare hostnames, so
 * we BROWSE that type, resolve each instance, and match the one whose resolved
 * host matches the requested `<name>.local`. This is robust to the service
 * instance being named differently from its `.local` hostname.
 *
 * Runs at RNS start (see [PythonRnsRuntime.start]), so it re-resolves on every
 * (re)connect — a changed RNode IP is picked up automatically.
 *
 * Best-effort: on timeout/failure the original `.local` host passes through
 * unchanged, and startup is never blocked beyond the timeouts below.
 */
internal object MdnsResolver {
    private const val TAG = "MdnsResolver"
    private const val SERVICE_TYPE = "_reticulum._tcp."
    private const val BROWSE_TIMEOUT_MS = 5000L
    private const val RESOLVE_TIMEOUT_MS = 3000L

    fun resolveInterfaces(context: Context, config: ReticulumConfig): ReticulumConfig {
        val needsResolve = config.enabledInterfaces.any {
            it is InterfaceConfig.TCPClient && isDotLocal(it.targetHost)
        }
        if (!needsResolve) return config

        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: run {
                Log.w(TAG, "NsdManager unavailable; leaving .local hosts unresolved")
                return config
            }

        // Browse + resolve the whole _reticulum._tcp set once, build a
        // host -> ip map, then apply it to every .local interface. One browse
        // covers all interfaces on the same service type.
        val hostMap = discover(nsd)
        if (hostMap.isEmpty()) {
            Log.w(TAG, "No $SERVICE_TYPE instances resolved; .local hosts pass through")
        }

        val resolved = config.enabledInterfaces.map { iface ->
            if (iface is InterfaceConfig.TCPClient && isDotLocal(iface.targetHost)) {
                val key = normalise(iface.targetHost)
                val ip = hostMap[key]
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

    /** true for "<name>.local" (optionally trailing dot), case-insensitive. */
    private fun isDotLocal(host: String): Boolean =
        host.trim().lowercase().removeSuffix(".").endsWith(".local")

    /** Canonical key: lowercase, no trailing dot. "IPRNode.local." -> "iprnode.local". */
    private fun normalise(host: String): String =
        host.trim().lowercase().removeSuffix(".")

    /**
     * Browse [SERVICE_TYPE], resolve each discovered instance, and return a
     * map of resolved-hostname -> numeric IP. Keys are normalised the same way
     * as the lookup so a "<name>.local" targetHost matches.
     */
    @SuppressLint("NewApi")
    private fun discover(nsd: NsdManager): Map<String, String> {
        val out = ConcurrentHashMap<String, String>()
        val browseDone = CountDownLatch(1)
        val pending = ConcurrentHashMap.newKeySet<String>()
        // Latch that fires when the browse settles; individual resolves race
        // against RESOLVE_TIMEOUT_MS.
        val resolveLatches = ConcurrentHashMap<String, CountDownLatch>()

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.d(TAG, "browse start failed (err=$errorCode)")
                browseDone.countDown()
            }
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String?) {}
            override fun onDiscoveryStopped(serviceType: String?) { browseDone.countDown() }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName
                pending.add(name)
                val latch = CountDownLatch(1)
                resolveLatches[name] = latch
                resolveOne(nsd, serviceInfo) { host, ip ->
                    if (ip != null) {
                        // Key by the service INSTANCE name (<name>.local) — this
                        // is what the user typed. RNodes advertise their hostname
                        // as "<ip>.local", so the instance name is the reliable
                        // link back to "iprnode.local".
                        out["${name.lowercase()}.local"] = ip
                        // Also key by the resolved hostname for devices that
                        // advertise a real <name>.local hostname.
                        if (host != null) out[host] = ip
                    }
                    latch.countDown()
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
        }

        return try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            // Let the browse run, then stop and wait for in-flight resolves.
            browseDone.await(BROWSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            runCatching { nsd.stopServiceDiscovery(discoveryListener) }
            resolveLatches.values.forEach { it.await(RESOLVE_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
            out.toMap()
        } catch (t: Throwable) {
            Log.w(TAG, "discover threw", t)
            runCatching { nsd.stopServiceDiscovery(discoveryListener) }
            out.toMap()
        }
    }

    /** Resolve one instance; callback with (normalised host, ip) or (null,null). */
    @SuppressLint("NewApi")
    private fun resolveOne(
        nsd: NsdManager,
        info: NsdServiceInfo,
        cb: (String?, String?) -> Unit,
    ) {
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.d(TAG, "resolve failed for ${info.serviceName} (err=$errorCode)")
                cb(null, null)
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                val host = hostnameOf(serviceInfo)
                val ip = ipOf(serviceInfo)
                if (host != null && ip != null) {
                    Log.d(TAG, "instance ${serviceInfo?.serviceName}: $host -> $ip")
                    cb(host, ip)
                } else {
                    cb(null, null)
                }
            }
        }
        try {
            @Suppress("DEPRECATION")
            nsd.resolveService(info, listener)
        } catch (t: Throwable) {
            Log.d(TAG, "resolveService threw for ${info.serviceName}", t)
            cb(null, null)
        }
    }

    /** Resolved host as "<name>.local", normalised, or null. */
    private fun hostnameOf(info: NsdServiceInfo?): String? {
        if (info == null) return null
        val raw: String? =
            if (Build.VERSION.SDK_INT >= 34) {
                @Suppress("DEPRECATION")
                info.hostAddresses.firstOrNull()?.hostName ?: info.host?.hostName
            } else {
                @Suppress("DEPRECATION")
                info.host?.hostName
            }
        if (raw.isNullOrBlank()) return null
        val h = raw.lowercase().removeSuffix(".")
        return if (h.endsWith(".local")) h else "$h.local"
    }

    private fun ipOf(info: NsdServiceInfo?): String? {
        if (info == null) return null
        val addrs: List<InetAddress> =
            if (Build.VERSION.SDK_INT >= 34) {
                @Suppress("DEPRECATION")
                info.hostAddresses.ifEmpty { info.host?.let { listOf(it) } ?: emptyList() }
            } else {
                @Suppress("DEPRECATION")
                info.host?.let { listOf(it) } ?: emptyList()
            }
        return (addrs.firstOrNull { it.address?.size == 4 } ?: addrs.firstOrNull())?.hostAddress
    }
}
