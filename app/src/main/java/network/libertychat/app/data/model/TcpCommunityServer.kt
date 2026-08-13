package network.libertychat.app.data.model

/**
 * Represents a community TCP server for Reticulum networking.
 *
 * @param name User-friendly name for the server
 * @param host Hostname or IP address
 * @param port TCP port number
 * @param isBootstrap When true, this server is recommended as a bootstrap interface.
 *                    Bootstrap interfaces auto-detach once sufficient discovered
 *                    interfaces are connected (RNS 1.1.0+ feature).
 */
data class TcpCommunityServer(
    val name: String,
    val host: String,
    val port: Int,
    val isBootstrap: Boolean = false,
)

/**
 * List of known community TCP servers for Reticulum.
 *
 * Selected servers are marked as bootstrap candidates based on:
 * - Reputation in the community
 * - Long-term reliability
 * - Geographic distribution
 */
object TcpCommunityServers {
    val servers: List<TcpCommunityServer> =
        listOf(
            // LCS: official Liberty Communication Systems public node (bootstrap interface)
            TcpCommunityServer("LCS Public Node", "public.lcs.network", 4245, isBootstrap = true),
            // LCS: local-network RNode — bootstrap interface OFF so it stays connected
            TcpCommunityServer("Local IP RNode", "iprnode.local", 4545, isBootstrap = false),
        )

    /**
     * Get only servers marked as bootstrap candidates.
     */
    val bootstrapServers: List<TcpCommunityServer>
        get() = servers.filter { it.isBootstrap }
}
