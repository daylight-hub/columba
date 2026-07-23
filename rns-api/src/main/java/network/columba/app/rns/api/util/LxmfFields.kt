package network.columba.app.rns.api.util

/**
 * LXMF protocol field IDs Columba reads or writes.
 *
 * The numeric values are upstream LXMF spec (`LXMF/LXMF.py`). Centralised
 * here so both backends (kotlin-native + python-flavor) and the UI process
 * all reference one definition — previously these were declared three
 * separate times in `:rns-backend-py` (once each in `PythonEventBridge`,
 * `PythonRnsLxmf`, `PythonRnsTelemetry`) plus referenced via lxmf-kt's
 * `LXMFConstants` on the kotlin backend.
 *
 * Only fields Columba *actually uses* on the wire are listed; the full
 * LXMF surface (audio modes, propagation metadata IDs, states, ...) lives
 * in lxmf-kt's `LXMFConstants` for the native backend and isn't relevant
 * to the Python flavor.
 */
object LxmfFields {
    /**
     * LXMF app name for delivery destinations — matches
     * `LXMF.LXMRouter.APP_NAME` upstream and the kotlin port's
     * `LXMFConstants.APP_NAME`. Combined with [DELIVERY_ASPECT] this is the
     * `<identity>.lxmf.delivery` destination Columba peers send to.
     */
    const val APP_NAME = "lxmf"

    /** Local aspect for LXMF delivery destinations (`LXMRouter.DELIVERY_ASPECT`). */
    const val DELIVERY_ASPECT = "delivery"

    /** Single-shot telemetry payload (Sideband-compatible location JSON). */
    const val FIELD_TELEMETRY = 0x02

    /** Multi-entry telemetry stream — propagation collector responses. */
    const val FIELD_TELEMETRY_STREAM = 0x03

    /** `[name, fgRgbBytes, bgRgbBytes]` — Sideband/MeshChat icon appearance. */
    const val FIELD_ICON_APPEARANCE = 0x04

    /** Sideband-compatible file attachments. */
    const val FIELD_FILE_ATTACHMENTS = 0x05

    /** Image payload `[format, bytes]`. */
    const val FIELD_IMAGE = 0x06

    /** Audio payload `[mode, bytes]`. */
    const val FIELD_AUDIO = 0x07

    // Audio modes for the data structure in [FIELD_AUDIO]. Values are taken
    // verbatim from upstream LXMF (`LXMF/LXMF.py`, the `AM_*` constants) so
    // clips interoperate with Sideband and any other LXMF client.
    // --- Codec2 modes ---------------------------------------------------
    // Sideband has no bindings for 450 / 450PWB (`audioproc.py:codec2_modes`
    // leaves both commented out), so 0x01/0x02 are dead on the wire in both
    // directions. Declared for completeness; never emitted, never decoded.

    /** Codec2 450 bps, wideband. No Sideband bindings — do not emit. */
    const val AM_CODEC2_450PWB = 0x01

    /** Codec2 450 bps. No Sideband bindings — do not emit. */
    const val AM_CODEC2_450 = 0x02

    /** Codec2 700C — 4 bytes per 40 ms frame (100 B/s). Cheapest interoperable mode. */
    const val AM_CODEC2_700C = 0x03

    /** Codec2 1200 bps — 6 bytes per 40 ms frame (150 B/s). LCS default for LoRa links. */
    const val AM_CODEC2_1200 = 0x04

    /** Codec2 1300 bps — 7 bytes per 40 ms frame. No saving over 1400; prefer that. */
    const val AM_CODEC2_1300 = 0x05

    /** Codec2 1400 bps — 7 bytes per 40 ms frame (175 B/s). */
    const val AM_CODEC2_1400 = 0x06

    /** Codec2 1600 bps — 8 bytes per 40 ms frame (200 B/s). */
    const val AM_CODEC2_1600 = 0x07

    /** Codec2 2400 bps — 6 bytes per 20 ms frame (300 B/s). Sideband's own PTT default. */
    const val AM_CODEC2_2400 = 0x08

    /** Codec2 3200 bps — 8 bytes per 20 ms frame (400 B/s). */
    const val AM_CODEC2_3200 = 0x09

    // --- Opus modes -----------------------------------------------------

    /** Opus in an Ogg container — the general-purpose interoperable mode. */
    const val AM_OPUS_OGG = 0x10

    /** Opus, low bandwidth. */
    const val AM_OPUS_LBW = 0x11

    /** Opus, medium bandwidth. */
    const val AM_OPUS_MBW = 0x12

    /** Opus tuned for push-to-talk. */
    const val AM_OPUS_PTT = 0x13

    /** Opus, standard quality. */
    const val AM_OPUS_STANDARD = 0x16

    /** Non-standard payload; the receiver must sniff the container itself. */
    const val AM_CUSTOM = 0xFF

    /**
     * Codec2 modes both LCS and Sideband can actually decode.
     *
     * Sideband's playback (`core.py::ptt_playback`, `main.py::play_audio_field`)
     * accepts exactly `AM_CODEC2_700C`..`AM_CODEC2_3200` and `AM_OPUS_OGG`;
     * anything else hits `raise NotImplementedError(audio_field[0])`. Keeping
     * the bound here means the send path can never emit something a Sideband
     * peer will refuse, and the receive path has one predicate to dispatch on.
     */
    val CODEC2_INTEROP_MODES = AM_CODEC2_700C..AM_CODEC2_3200

    /** True when [mode] is a Codec2 payload we can decode (raw concatenated frames). */
    fun isCodec2AudioMode(mode: Int): Boolean = mode in CODEC2_INTEROP_MODES

    /** True when [mode] is Opus in an Ogg container (playable by `MediaPlayer` as-is). */
    fun isOpusOggAudioMode(mode: Int): Boolean = mode == AM_OPUS_OGG

    /**
     * True when a `FIELD_AUDIO` payload in [mode] can be rendered locally.
     * Codec2 additionally requires the native decoder — see `Codec2Codec`.
     */
    fun isPlayableAudioMode(mode: Int): Boolean =
        isOpusOggAudioMode(mode) || isCodec2AudioMode(mode)

    /** Command structures (Sideband telemetry-request RPCs). */
    const val FIELD_COMMANDS = 0x09

    /**
     * Canonical tap-back reaction field — `fields[0x40] = {0x00: bytes, 0x01: bytes}`
     * per-event wire shape, standardised upstream in LXMF.py (commit
     * `764758d`, "to be finalized in 1.0.0"):
     *   - [REACTION_TO] (`0x00`) = raw bytes of the target `LXMessage.hash`.
     *   - [REACTION_CONTENT] (`0x01`) = UTF-8 bytes of the reaction content
     *     (the emoji). The sender is NOT carried on the wire — it is derived
     *     from the inbound LXMF message's source hash on receive.
     *
     * One reaction per LXMessage on the wire; the receiver aggregates
     * per-target-message locally (the flat `reactionsJson` column) for UI
     * rendering. Outbound writes this shape only; inbound parsing falls back
     * to the legacy [FIELD_REACTION_LEGACY] for un-upgraded Columba peers —
     * see `ReactionWireCodec`.
     */
    const val FIELD_REACTION = 0x40

    /** [FIELD_REACTION] dict key — raw bytes of the target `LXMessage.hash`. */
    const val REACTION_TO = 0x00

    /** [FIELD_REACTION] dict key — UTF-8 bytes of the reaction content (emoji). */
    const val REACTION_CONTENT = 0x01

    /**
     * Legacy tap-back reaction field — `fields[0x10] = {reaction_to, emoji, sender}`
     * string-keyed dict (hex-string target + sender, Unicode emoji), shared
     * historically with MeshChatX
     * (`src/backend/lxmf_utils.py:11 LXMF_APP_EXTENSIONS_FIELD = 16`).
     *
     * **Parse-only fallback.** Outbound no longer writes this — it was
     * superseded by the canonical [FIELD_REACTION] (`0x40`) once upstream
     * LXMF standardised the field. `0x10` also now sits inside upstream's
     * reserved `0x00`–`0x80` range, so squatting it risks a future
     * collision. Kept on the inbound path so reactions from un-upgraded
     * Columba peers still resolve — see `ReactionWireCodec`.
     */
    const val FIELD_REACTION_LEGACY = 0x10

    /**
     * Reply-target message hash — `fields[0x30] = ByteArray(32)` raw
     * bytes (NOT a hex string). MeshChatX format
     * (`meshchat.py:16697`). Saves ~32 bytes on the wire per reply vs.
     * the hex-string-in-dict overload Columba previously used at 0x10.
     *
     * Inbound parse may also fall back to a legacy
     * `fields[0x10] = {reply_to: "<hex>"}` shape for un-upgraded
     * Columba peers — see `MessageMapper.parseReplyToFromFields`.
     */
    const val FIELD_REPLY_HASH = 0x30

    /**
     * Optional reply quoted-content — `fields[0x31] = ByteArray` UTF-8
     * bytes of the original message's content the sender saw. Lets the
     * recipient render the reply preview even when they don't have the
     * original message in their local store (cross-app interop case;
     * also covers OPP-only peers whose local store cycles). MeshChatX
     * format (`meshchat.py:16698`).
     */
    const val FIELD_REPLY_QUOTE = 0x31

    /**
     * Upstream LXMF `FIELD_CUSTOM_META` (0xFD) — documented extension point
     * for app-specific metadata that other LXMF clients should ignore.
     * Columba uses this to carry the `cease` / `expires` / `approxRadius`
     * extras that ride alongside a Sideband-compatible
     * [FIELD_TELEMETRY] location share. Sideband's `core.py` has zero
     * references to FIELD_CUSTOM_* — interop-safe.
     *
     * Previously this was a Columba-invented `0x70`; flipped to upstream's
     * canonical 0xFD because invented field IDs in the unassigned range
     * risk collision if upstream LXMF later assigns them. See also
     * [LocationTelemetry.COLUMBA_META_FIELD_ID].
     */
    const val FIELD_CUSTOM_META = 0xFD
}
