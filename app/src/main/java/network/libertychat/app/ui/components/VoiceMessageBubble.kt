package network.libertychat.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import network.libertychat.app.rns.api.util.LxmfFields

/**
 * LCS: bubble for an LXMF `FIELD_AUDIO` (0x07) voice message.
 *
 * Inbound clips autoplay once on receipt when the conversation is open (see
 * `MessageCollector.maybeAutoplayVoiceMessage`). This bubble is what makes them
 * replayable afterwards — and what makes them visible at all, since a PTT
 * message carries no text beyond the single space Sideband uses as a
 * placeholder for attachment-only messages.
 *
 * Three states, driven by what the sender's codec was and whether this device
 * can decode it:
 *
 * - **Playable** — tap to play, tap again to stop.
 * - **Playing** — shows a stop affordance.
 * - **Unsupported** — a Codec2 clip on an ABI with no `libcodec2.so` (x86_64
 *   emulators), or a mode outside the range Sideband and LCS agree on. Tapping
 *   does nothing; the label says why rather than failing silently.
 *
 * @param audioMode LXMF `AM_*` mode from `FIELD_AUDIO[0]`.
 * @param isPlayable Whether this build/device can decode [audioMode] — pass
 *   `VoiceMessagePlayer.canPlay(audioMode)`. Kept as a parameter rather than
 *   computed here so the composable stays free of the native probe.
 */
@Composable
fun VoiceMessageBubble(
    audioMode: Int,
    isPlayable: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (isPlayable) Modifier.clickable { onTogglePlayback() } else Modifier,
                ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                isLoading ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )

                !isPlayable ->
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                isPlaying ->
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop playback",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )

                else ->
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play voice message",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
            }

            Column {
                Text(
                    text = "Voice message",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text =
                        if (isPlayable) {
                            audioModeLabel(audioMode)
                        } else {
                            "${audioModeLabel(audioMode)} — not supported on this device"
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Human-readable name for an LXMF `AM_*` audio mode.
 *
 * Covers the modes Sideband can actually produce; anything else falls through
 * to the raw hex so an unexpected clip is still diagnosable from a screenshot.
 */
private fun audioModeLabel(mode: Int): String =
    when (mode) {
        LxmfFields.AM_CODEC2_700C -> "Codec2 700C"
        LxmfFields.AM_CODEC2_1200 -> "Codec2 1200"
        LxmfFields.AM_CODEC2_1300 -> "Codec2 1300"
        LxmfFields.AM_CODEC2_1400 -> "Codec2 1400"
        LxmfFields.AM_CODEC2_1600 -> "Codec2 1600"
        LxmfFields.AM_CODEC2_2400 -> "Codec2 2400"
        LxmfFields.AM_CODEC2_3200 -> "Codec2 3200"
        LxmfFields.AM_OPUS_OGG -> "Opus"
        else -> "Audio mode 0x${mode.toString(16)}"
    }
