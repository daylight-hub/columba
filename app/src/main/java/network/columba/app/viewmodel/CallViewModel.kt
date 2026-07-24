package network.columba.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import network.columba.app.data.repository.AnnounceRepository
import network.columba.app.data.repository.ContactRepository
import network.columba.app.rns.api.RnsTelephony
import network.columba.app.rns.api.model.CallState
import network.columba.app.ui.model.CodecProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for voice call screens.
 *
 * Observes call state via [RnsTelephony] (the `:rns-api` seam) and uses
 * the same surface for the network IPC actions (initiate / answer /
 * hangup / mute / speaker / decline) and the local-state mutators
 * (setConnecting / setEnded / setMutedLocally / setSpeakerLocally /
 * setPttModeLocally / setPttActiveLocally).
 *
 * Replaces A.9's split between the legacy `ReticulumProtocol` (for IPC)
 * and `CallCoordinator` (for in-process state). Post-A.10 there is one
 * seam contract that survives the AIDL boundary.
 */
@Suppress("TooManyFunctions") // Call + PTT controls require many small action methods
@HiltViewModel
class CallViewModel
    @Inject
    constructor(
        private val contactRepository: ContactRepository,
        private val announceRepository: AnnounceRepository,
        private val telephony: RnsTelephony,
    ) : ViewModel() {
        companion object {
            private const val TAG = "CallViewModel"
        }

        // Serializes mute IPC calls to prevent race conditions (e.g. PTT release vs toggle off)
        private val muteMutex = Mutex()

        /**
         * LCS: emitted when the *peer* moves the call between duplex modes.
         *
         * Half duplex is symmetric — a peer switching squelches this device's
         * transmitter too — so the mic can go dead with nothing on screen
         * explaining why. No new IPC is needed to detect it: this ViewModel is
         * the only thing that asks for a local switch, so any other transition
         * of [isPttMode] came from the peer.
         *
         * `true` = peer switched to half duplex.
         */
        private val _peerDuplexChange = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
        val peerDuplexChange: SharedFlow<Boolean> = _peerDuplexChange.asSharedFlow()

        /** Mode this device just asked for; the echo back is not a peer change. */
        @Volatile
        private var expectedDuplexMode: Boolean? = null

        init {
            viewModelScope.launch {
                // drop(1): the current value is the starting state, not a change.
                telephony.isPttMode.drop(1).collect { halfDuplex ->
                    when {
                        expectedDuplexMode == halfDuplex -> expectedDuplexMode = null
                        // The host clears isPttMode on call teardown; that is not
                        // the peer switching modes.
                        callState.value !is CallState.Active -> Unit
                        else -> _peerDuplexChange.tryEmit(halfDuplex)
                    }
                }
            }
        }

        // Expose call state from telephony seam
        val callState: StateFlow<CallState> = telephony.callState
        val isMuted: StateFlow<Boolean> = telephony.isMuted
        val isSpeakerOn: StateFlow<Boolean> = telephony.isSpeakerOn
        val isPttMode: StateFlow<Boolean> = telephony.isPttMode
        val isPttActive: StateFlow<Boolean> = telephony.isPttActive
        val remoteIdentity: StateFlow<String?> = telephony.remoteIdentity

        // Call duration (updated every second during active call)
        private val _callDuration = MutableStateFlow(0L)
        val callDuration: StateFlow<Long> = _callDuration.asStateFlow()

        // Peer display name (resolved from contacts)
        private val _peerName = MutableStateFlow<String?>(null)
        val peerName: StateFlow<String?> = _peerName.asStateFlow()

        // Loading state for call initiation
        private val _isConnecting = MutableStateFlow(false)
        val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

        // ---------------------------------------------------------------
        // LCS: mid-call codec state
        // ---------------------------------------------------------------

        /** Codec the call is currently running on. */
        private val _activeProfile = MutableStateFlow(CodecProfile.DEFAULT)
        val activeProfile: StateFlow<CodecProfile> = _activeProfile.asStateFlow()

        /**
         * Codec that fits the measured link, or null when the link was never
         * measured or is comfortably fast enough for [activeProfile].
         */
        private val _recommendedProfile = MutableStateFlow<CodecProfile?>(null)
        val recommendedProfile: StateFlow<CodecProfile?> = _recommendedProfile.asStateFlow()

        /** Measured link rate in bits per second, or null if unknown. */
        private val _measuredBps = MutableStateFlow<Int?>(null)
        val measuredBps: StateFlow<Int?> = _measuredBps.asStateFlow()

        /** Set once the user dismisses the advisory; cleared on the next call. */
        private val _advisoryDismissed = MutableStateFlow(false)
        val advisoryDismissed: StateFlow<Boolean> = _advisoryDismissed.asStateFlow()

        // Track duration timer job to prevent multiple concurrent timers
        private var durationTimerJob: kotlinx.coroutines.Job? = null

        init {
            // Track call duration when active
            viewModelScope.launch {
                callState.collect { state ->
                    when (state) {
                        is CallState.Active -> {
                            startDurationTimer()
                        }
                        is CallState.Ended, CallState.Idle -> {
                            durationTimerJob?.cancel()
                            durationTimerJob = null
                            _callDuration.value = 0L
                            _isConnecting.value = false
                        }
                        is CallState.Connecting -> {
                            _isConnecting.value = true
                        }
                        else -> {
                            _isConnecting.value = false
                        }
                    }
                }
            }

            // Resolve peer name from identity
            viewModelScope.launch {
                telephony.remoteIdentity.filterNotNull().collect { hash ->
                    resolvePeerName(hash)
                }
            }
        }

        private fun startDurationTimer() {
            // Cancel any existing timer to prevent multiple concurrent timers
            // This handles ViewModel recreation, multiple collectors, etc.
            durationTimerJob?.cancel()

            durationTimerJob =
                viewModelScope.launch {
                    // Reset duration at start of call
                    _callDuration.value = 0L
                    // Increment every second while this job is active
                    while (true) {
                        delay(1000)
                        _callDuration.value += 1
                    }
                }
        }

        /**
         * Resolve display name for a peer with priority:
         * 1. Contact's custom nickname (user-set)
         * 2. Announce peer name (from network) - by destination hash
         * 3. Announce peer name (from network) - by identity hash (for LXST calls)
         * 4. Formatted identity hash (fallback)
         */
        private suspend fun resolvePeerName(identityHash: String) {
            try {
                // Check contact for custom nickname first
                val contact = contactRepository.getContact(identityHash)
                if (!contact?.customNickname.isNullOrBlank()) {
                    _peerName.value = contact!!.customNickname
                    return
                }

                // Check announce for peer name by destination hash
                val announce = announceRepository.getAnnounce(identityHash)
                if (!announce?.peerName.isNullOrBlank()) {
                    _peerName.value = announce!!.peerName
                    return
                }

                // For LXST calls, the hash might be an identity hash rather than destination hash
                // (different aspects produce different destination hashes for the same identity)
                val announceByIdentity = announceRepository.findByIdentityHash(identityHash)
                if (!announceByIdentity?.peerName.isNullOrBlank()) {
                    Log.d(TAG, "Found peer name via identity hash: ${announceByIdentity!!.peerName}")
                    _peerName.value = announceByIdentity.peerName
                    return
                }

                // Fallback to formatted hash
                _peerName.value = formatIdentityHash(identityHash)
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving peer name", e)
                _peerName.value = formatIdentityHash(identityHash)
            }
        }

        private fun formatIdentityHash(hash: String): String =
            if (hash.length > 12) {
                "${hash.take(6)}...${hash.takeLast(6)}"
            } else {
                hash
            }

        /**
         * Initiate an outgoing call.
         * Uses RnsTelephony for IPC + state updates.
         * Retries if CallManager not yet initialized (can take time after app install).
         *
         * @param destinationHash Hex string of destination identity hash
         * @param profileCode LXST codec profile code (0x10-0x80), or null to use default
         */
        fun initiateCall(
            destinationHash: String,
            profileCode: Int? = null,
            halfDuplex: Boolean = false,
        ) {
            // The host will flip isPttMode when the mode is applied at ring
            // time; that echo is this device's own request, not the peer's.
            expectedDuplexMode = if (halfDuplex) true else null
            Log.w(TAG, "📞📞📞 initiateCall() CALLED - destHash=${destinationHash.take(16)}, profile=${profileCode ?: "default"}...")
            Log.w(TAG, "📞 Current callState=${callState.value}")
            _isConnecting.value = true
            resolvePeerNameSync(destinationHash)

            // Update local state then initiate via service IPC with retry for CallManager init
            viewModelScope.launch {
                telephony.setConnecting(destinationHash)

                var retryCount = 0
                val maxRetries = 10
                val retryDelayMs = 1000L

                while (retryCount < maxRetries) {
                    Log.w(TAG, "📞 Calling telephony.initiateCall() (attempt ${retryCount + 1}/$maxRetries)...")
                    val result = telephony.initiateCall(destinationHash, profileCode, halfDuplex)
                    Log.w(TAG, "📞 telephony.initiateCall() returned: success=${result.isSuccess}")

                    if (result.isSuccess) {
                        Log.w(TAG, "📞✅ Call initiated successfully!")
                        return@launch
                    }

                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"

                    // Retry if CallManager not initialized yet
                    if (errorMsg.contains("not initialized", ignoreCase = true)) {
                        retryCount++
                        if (retryCount < maxRetries) {
                            Log.w(TAG, "📞 CallManager not ready, retrying in ${retryDelayMs}ms...")
                            kotlinx.coroutines.delay(retryDelayMs)
                            continue
                        }
                    }

                    // Non-retryable error or max retries reached
                    Log.e(TAG, "📞❌ Failed to initiate call: $errorMsg")
                    _isConnecting.value = false
                    telephony.setEnded()
                    return@launch
                }
            }
        }

        private fun resolvePeerNameSync(identityHash: String) {
            viewModelScope.launch {
                resolvePeerName(identityHash)
            }
        }

        /**
         * Answer an incoming call.
         */
        fun answerCall() {
            Log.d(TAG, "Answering call")
            viewModelScope.launch {
                val result = telephony.answerCall()
                if (result.isFailure) {
                    Log.e(TAG, "Failed to answer call: ${result.exceptionOrNull()?.message}")
                }
            }
        }

        /**
         * Decline an incoming call.
         */
        fun declineCall() {
            Log.d(TAG, "Declining call")
            endCall()
        }

        /**
         * End the current call.
         */
        fun endCall() {
            Log.d(TAG, "Ending call")
            viewModelScope.launch {
                telephony.hangupCall()
                telephony.setEnded()
            }
        }

        /**
         * Toggle microphone mute.
         */
        /**
         * LCS: record what the link measured before dialling.
         *
         * Called from the call screen with the probe the caller already ran to
         * choose a codec. Nothing probes again during the call: LXST exposes no
         * mid-call quality telemetry, so a live measurement would mean sending
         * probe traffic over the same link the audio is struggling on — which
         * on a ~1 kbps LoRa link would cause the problem it was measuring.
         *
         * @param bandwidthBps conservative link estimate, or null if unmeasured.
         * @param profile codec the call actually opened with.
         */
        fun recordLinkMeasurement(
            bandwidthBps: Long?,
            profile: CodecProfile,
        ) {
            _activeProfile.value = profile
            _advisoryDismissed.value = false

            if (bandwidthBps == null) {
                _measuredBps.value = null
                _recommendedProfile.value = null
                return
            }

            _measuredBps.value = bandwidthBps.toInt()
            _recommendedProfile.value =
                if (CodecProfile.isTooHeavyFor(profile, bandwidthBps)) {
                    CodecProfile.recommendFromBandwidth(bandwidthBps)
                } else {
                    null
                }
        }

        /**
         * LCS: change codec on the live call.
         *
         * LXST reconfigures the local pipeline and signals the peer, whose own
         * LXST follows — so this moves both ends, including Sideband and
         * MeshChat peers, since the signalling is upstream protocol.
         *
         * Works in both directions: this is also how a call goes back up to
         * Opus after a downgrade, or after moving onto a faster interface.
         */
        fun switchCodec(profile: CodecProfile) {
            viewModelScope.launch {
                val result = telephony.switchCallProfile(profile.code)
                result
                    .onSuccess {
                        Log.i(TAG, "Switched call codec to ${profile.displayName}")
                        _activeProfile.value = profile
                        // The advisory has served its purpose once acted on.
                        _recommendedProfile.value = null
                    }.onFailure {
                        Log.e(TAG, "Failed to switch codec to ${profile.displayName}", it)
                    }
            }
        }

        /** LCS: hide the advisory for the remainder of this call. */
        fun dismissAdvisory() {
            _advisoryDismissed.value = true
        }

        fun toggleMute() {
            val newMuted = !telephony.isMuted.value
            viewModelScope.launch {
                telephony.setMutedLocally(newMuted)
                muteMutex.withLock { telephony.setCallMuted(newMuted) }
            }
        }

        /**
         * Toggle speaker/earpiece.
         */
        fun toggleSpeaker() {
            val newSpeaker = !telephony.isSpeakerOn.value
            viewModelScope.launch {
                telephony.setSpeakerLocally(newSpeaker)
                telephony.setCallSpeaker(newSpeaker)
            }
        }

        /**
         * Toggle push-to-talk mode.
         *
         * When enabled, transmit is muted by default. The user must press
         * and hold the PTT button (on-screen or Bluetooth headset) to transmit.
         */
        fun togglePttMode() {
            val newMode = !telephony.isPttMode.value
            expectedDuplexMode = newMode
            viewModelScope.launch {
                // Optimistic UI; the host re-asserts isPttMode when the mode is
                // actually applied, and also when the *peer* initiates a switch.
                telephony.setPttModeLocally(newMode)
                telephony.setPttActiveLocally(false)

                // True half duplex (LXST >= 0.5.0): squelch the transmitter and
                // signal the peer, which follows. Deliberately NOT a mic mute —
                // a mute keeps transmitting encoded silence at full frame rate.
                // The mute button stays an independent axis, as it is in LXST.
                telephony.setCallDuplexMode(halfDuplex = newMode)
            }
        }

        /**
         * Set PTT transmit state (press/release).
         *
         * @param active true when pressed (transmitting), false when released (listening)
         */
        fun setPttActive(active: Boolean) {
            if (!telephony.isPttMode.value) return
            if (callState.value !is CallState.Active) return
            viewModelScope.launch {
                telephony.setPttActiveLocally(active)
                // Squelch, not mute. Also off the mute mutex on purpose: this is
                // a single atomic flag, and serialising it behind the mute lock
                // adds latency exactly where it is most audible (key-up clipping).
                telephony.setCallPttActive(active)
            }
        }

        /**
         * Check if there's an active call.
         */
        fun hasActiveCall(): Boolean = telephony.hasActiveCall()

        /**
         * Format duration as MM:SS string.
         */
        fun formatDuration(seconds: Long): String {
            val mins = seconds / 60
            val secs = seconds % 60
            return String.format(Locale.US, "%02d:%02d", mins, secs)
        }

        /**
         * Get call status text for UI display.
         */
        fun getStatusText(state: CallState): String =
            when (state) {
                is CallState.Idle -> ""
                is CallState.Connecting -> "Connecting..."
                is CallState.Ringing -> "Ringing..."
                is CallState.Incoming -> "Incoming Call"
                is CallState.Active -> "Connected"
                is CallState.Busy -> "Line Busy"
                is CallState.Rejected -> "Call Rejected"
                is CallState.Ended -> "Call Ended"
            }
    }
