package network.columba.app.rns.api.call

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * LXST duplex-mode signalling (LXST >= 0.5.0, Sideband >= 2.0.0).
 *
 * A mode change is announced as `PREFERRED_MODE + mode` in the usual
 * `{FIELD_SIGNALLING(0x00): [...]}` msgpack envelope, i.e. `0xF1` for full
 * duplex and `0xF2` for half duplex.
 *
 * Two properties of the upstream protocol that this file has to preserve:
 *
 *  1. **Profile signals must be decoded first.** A profile signal is
 *     `PREFERRED_PROFILE (0xFF) + profile (0x10..0x80)` = `0x10F..0x17F`,
 *     which also satisfies `>= PREFERRED_MODE`. [isModeSignal] is therefore
 *     bounded above by `PREFERRED_PROFILE`.
 *  2. **The mode is symmetric.** A peer receiving `0xF2` applies half duplex
 *     to its *own* transmitter and does not echo the signal back, so one side
 *     switching moves the whole call. This mirrors `switch_profile`.
 *
 * Peers older than LXST 0.5.0 ignore `0xF1`/`0xF2` (they match no `STATUS_*`
 * and fall below `PREFERRED_PROFILE`), so sending them is safe.
 */
object DuplexSignalling {
    const val PREFERRED_MODE = 0xF0
    const val PREFERRED_PROFILE = 0xFF

    const val MODE_FULL_DUPLEX = 0x01
    const val MODE_HALF_DUPLEX = 0x02

    const val SIGNAL_FULL_DUPLEX = PREFERRED_MODE + MODE_FULL_DUPLEX
    const val SIGNAL_HALF_DUPLEX = PREFERRED_MODE + MODE_HALF_DUPLEX

    /** True for `0xF0..0xFE` — the mode namespace, excluding profile signals. */
    fun isModeSignal(signal: Int): Boolean = signal in PREFERRED_MODE until PREFERRED_PROFILE

    fun isHalfDuplexSignal(signal: Int): Boolean =
        signal - PREFERRED_MODE == MODE_HALF_DUPLEX

    fun signalFor(halfDuplex: Boolean): Int =
        if (halfDuplex) SIGNAL_HALF_DUPLEX else SIGNAL_FULL_DUPLEX
}

/**
 * Host-side duplex state and transmit squelch for one device.
 *
 * "Squelched" means **no packets on the air** — the call manager drops the
 * frame at the `AudioPacketHandler` boundary rather than transmitting encoded
 * silence, which is what a mic mute does. On a 700–3200 bps Codec2 link that
 * distinction is the entire point of half duplex.
 *
 * Entering half duplex squelches immediately (mic dead until the first PTT
 * press), matching LXST's `__select_call_mode`.
 *
 * [shouldTransmit] is read on the audio/IO path for every outbound frame and
 * is a plain [AtomicBoolean] read — no locks, no allocation.
 */
class DuplexModeController {
    private val _isHalfDuplex = MutableStateFlow(false)

    /** True while the call is half duplex. Reflects peer-initiated switches. */
    val isHalfDuplex: StateFlow<Boolean> = _isHalfDuplex.asStateFlow()

    private val squelched = AtomicBoolean(false)

    /** Audio hot path — gates every outbound frame. */
    fun shouldTransmit(): Boolean = !squelched.get()

    val isSquelched: Boolean get() = squelched.get()

    /**
     * Apply a duplex mode, local or peer-initiated. Entering half duplex
     * squelches; leaving it opens the transmitter.
     *
     * @return true if the mode actually changed.
     */
    fun applyMode(halfDuplex: Boolean): Boolean {
        val changed = _isHalfDuplex.value != halfDuplex
        _isHalfDuplex.value = halfDuplex
        squelched.set(halfDuplex)
        return changed
    }

    /** PTT press/release. No-op outside half duplex. */
    fun setPttActive(active: Boolean) {
        if (!_isHalfDuplex.value) return
        squelched.set(!active)
    }

    /** Back to full duplex, transmitter open. Called at call setup/teardown. */
    fun reset() {
        _isHalfDuplex.value = false
        squelched.set(false)
    }
}
