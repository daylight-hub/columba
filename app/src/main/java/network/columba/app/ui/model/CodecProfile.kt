package network.columba.app.ui.model

import network.columba.app.rns.api.model.LinkSpeedProbeResult

/**
 * Audio codec profiles for voice calls.
 *
 * These map to LXST's Telephony.Profiles constants:
 * - Codec2 profiles (0x10-0x30): Lower bandwidth, works over very slow links
 * - Opus profiles (0x40-0x60): Higher quality, requires more bandwidth
 * - Latency profiles (0x70-0x80): Optimized for low delay
 */
enum class CodecProfile(
    val code: Int,
    val displayName: String,
    val description: String,
    val isExperimental: Boolean = false,
    /**
     * LCS: shown as a badge in the codec picker. Non-null on the one profile
     * LCS recommends for voice over a LoRa/RNode link.
     */
    val lcsRecommendation: String? = null,
) {
    BANDWIDTH_ULTRA_LOW(
        code = 0x10,
        displayName = "Ultra Low Bandwidth",
        description = "Codec2 700C - Best for very slow connections",
    ),
    BANDWIDTH_VERY_LOW(
        code = 0x20,
        displayName = "Very Low Bandwidth",
        description = "Codec2 1600 - Good for slow connections",
    ),
    BANDWIDTH_LOW(
        code = 0x30,
        displayName = "Low Bandwidth",
        description = "Codec2 3200 - Balanced for limited bandwidth",
        // LCS: 3200 bps of payload (~400 B/s) is the most intelligible Codec2
        // mode that still fits a Short Fast RNode link (~10.9 kbps raw) with
        // room for RNS framing and retries. 700C and 1600 are for links slower
        // than that; Opus needs far more than any LoRa preset provides.
        lcsRecommendation = "Voice/PTT",
    ),
    QUALITY_MEDIUM(
        code = 0x40,
        displayName = "Medium Quality",
        description = "Opus - Good balance of quality and bandwidth",
    ),
    QUALITY_HIGH(
        code = 0x50,
        displayName = "High Quality",
        description = "Opus - Higher fidelity audio",
    ),
    QUALITY_MAX(
        code = 0x60,
        displayName = "Maximum Quality",
        description = "Opus - Best audio, requires more bandwidth",
    ),
    LATENCY_LOW(
        code = 0x80,
        displayName = "Low Latency",
        description = "Opus - Reduced delay, 20ms frames",
        isExperimental = true,
    ),
    LATENCY_ULTRA_LOW(
        code = 0x70,
        displayName = "Ultra Low Latency",
        description = "Opus - Minimized delay, 10ms frames",
        isExperimental = true,
    ),
    ;

    companion object {
        val DEFAULT = QUALITY_MEDIUM

        /**
         * LCS: tiers offered by the in-call codec selector.
         *
         * Deliberately a subset of [entries] — the dial-time dialog still lists
         * every profile. Mid-call the choice has to be quick and unambiguous, so
         * QUALITY_MAX is dropped (a poor fit for any radio link) along with both
         * LATENCY_* profiles, which are QUALITY_MEDIUM with different frame
         * timing and read as confusing peers of "Medium" in a five-item list.
         */
        val IN_CALL_TIERS: List<CodecProfile> =
            listOf(
                BANDWIDTH_ULTRA_LOW,
                BANDWIDTH_VERY_LOW,
                BANDWIDTH_LOW,
                QUALITY_MEDIUM,
                QUALITY_HIGH,
            )

        fun fromCode(code: Int): CodecProfile? = entries.find { it.code == code }

        /**
         * LCS: should this call default to half duplex at dial time?
         *
         * Keyed off the profile the link probe recommends rather than a second
         * bandwidth threshold, so "low bandwidth" means exactly one thing across
         * the dial dialog, the advisory, and this. The three tiers below are the
         * Codec2 ones (700C / 1600 / 3200 bps) — where full duplex over LoRa is
         * what actually breaks these calls.
         *
         * Only ever a *default*: the toggle is per-call and always overridable.
         */
        fun defaultsToHalfDuplex(recommended: CodecProfile): Boolean =
            recommended == BANDWIDTH_ULTRA_LOW ||
                recommended == BANDWIDTH_VERY_LOW ||
                recommended == BANDWIDTH_LOW

        /**
         * Get a conservative bandwidth estimate from link probe results.
         *
         * Uses min of establishment rate and next hop bitrate for safety.
         * The next hop interface bitrate only tells us about the first hop -
         * there could be slower hops further along the path in a mesh network.
         * The establishment rate measures actual end-to-end path performance.
         *
         * @param probe The link speed probe result
         * @return Conservative bandwidth estimate in bits per second, or null if no data
         */
        fun getConservativeBandwidthBps(probe: LinkSpeedProbeResult): Long? =
            getConservativeBandwidthBps(
                expectedRateBps = probe.expectedRateBps,
                establishmentRateBps = probe.establishmentRateBps,
                nextHopBitrateBps = probe.nextHopBitrateBps,
            )

        /**
         * LCS: rate-field overload.
         *
         * `LinkSpeedProbeResult` and `ConversationLinkManager.LinkState` are
         * unrelated types that happen to carry the same three rate figures —
         * one is the result of an explicit probe, the other the live state of
         * an established link. Taking the fields directly lets both feed the
         * same estimate without `CodecProfile` depending on either type.
         */
        fun getConservativeBandwidthBps(
            expectedRateBps: Long?,
            establishmentRateBps: Long?,
            nextHopBitrateBps: Long?,
        ): Long? {
            // Best case: actual measured throughput from prior transfers
            if (expectedRateBps != null && expectedRateBps > 0) {
                return expectedRateBps
            }
            val establishment = establishmentRateBps?.takeIf { it > 0 }
            val nextHop = nextHopBitrateBps?.takeIf { it > 0 }
            return when {
                establishment != null && nextHop != null -> minOf(establishment, nextHop)
                establishment != null -> establishment
                nextHop != null -> nextHop
                else -> null
            }
        }

        /**
         * Recommend a codec profile based on link probe results.
         *
         * Uses conservative bandwidth thresholds with headroom for overhead:
         * - Codec2 700C: recommend when < 2 kbps
         * - Codec2 1600: recommend when 2-6 kbps
         * - Codec2 3200: recommend when 6-24 kbps (every LoRa preset lands here)
         * - Opus low: recommend when 24-64 kbps
         * - Opus medium: recommend when 64-128 kbps
         * - Opus high: recommend when > 128 kbps
         *
         * @param probe The link speed probe result
         * @return Recommended codec profile based on available bandwidth
         */
        /** LCS: the Codec2 profiles, cheapest first. */
        fun codec2Profiles(): List<CodecProfile> =
            listOf(BANDWIDTH_ULTRA_LOW, BANDWIDTH_VERY_LOW, BANDWIDTH_LOW)

        /** LCS: the non-experimental Opus profiles, cheapest first. */
        fun opusProfiles(): List<CodecProfile> =
            listOf(QUALITY_MEDIUM, QUALITY_HIGH, QUALITY_MAX)

        /**
         * LCS: every codec offerable mid-call, cheapest first.
         *
         * Both families are present. Codec2 is what rescues a struggling LoRa
         * link, but the switch has to be reversible: a call that moved onto a
         * faster interface, or was downgraded pre-emptively, should be able to
         * go back to Opus without hanging up. LXST's `switchProfile` is
         * symmetric, so upgrading costs no more than downgrading.
         *
         * The experimental low-latency Opus profiles are excluded — mid-call is
         * the wrong place to discover an experimental codec.
         */
        fun switchableProfiles(): List<CodecProfile> = codec2Profiles() + opusProfiles()

        /**
         * LCS: true when [this] cannot be carried by a link measuring
         * [bandwidthBps], i.e. the recommendation ladder would place the link
         * below the profile currently in use.
         */
        fun isTooHeavyFor(
            profile: CodecProfile,
            bandwidthBps: Long,
        ): Boolean {
            val fits = recommendFromBandwidth(bandwidthBps)
            return fits.ordinal < profile.ordinal
        }

        /**
         * Ladder shared by [recommendFromProbe] and [isTooHeavyFor].
         *
         * LCS raised the Codec2/Opus boundary from 10 kbps to 24 kbps. The old
         * figure compared a codec's nominal bitrate against the link's *raw*
         * rate, which is far too optimistic on LoRa: a Short Fast RNode link
         * measures around 10.9 kbps raw, but after RNS framing, link overhead
         * and retries the usable throughput is a fraction of that — and Opus
         * needs sustained headroom, not a rate it can only just meet.
         *
         * The practical effect of the old boundary was that every RNode link
         * fast enough to be usable at all was told to run Opus, so the
         * recommendation never actually pointed at Codec2 where it mattered.
         */
        fun recommendFromBandwidth(bandwidthBps: Long): CodecProfile {
            val kbps = bandwidthBps / 1000.0
            return when {
                kbps < 2 -> BANDWIDTH_ULTRA_LOW // Codec2 700C
                kbps < 6 -> BANDWIDTH_VERY_LOW // Codec2 1600
                kbps < 24 -> BANDWIDTH_LOW // Codec2 3200 — covers all LoRa presets
                kbps < 64 -> QUALITY_MEDIUM // Opus low
                kbps < 128 -> QUALITY_HIGH // Opus medium
                else -> QUALITY_MAX // Opus high
            }
        }

        fun recommendFromProbe(probe: LinkSpeedProbeResult): CodecProfile {
            val bandwidthBps = getConservativeBandwidthBps(probe) ?: return DEFAULT
            return recommendFromBandwidth(bandwidthBps)
        }
    }
}
