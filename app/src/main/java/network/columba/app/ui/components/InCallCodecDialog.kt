package network.columba.app.ui.components

import androidx.compose.runtime.Composable
import network.columba.app.ui.model.CodecProfile

/**
 * LCS: codec picker for a call that is already up.
 *
 * Shows the five in-call tiers ([CodecProfile.IN_CALL_TIERS]) rather than every
 * profile — see the KDoc there for why the set is trimmed. The dial-time
 * [CodecSelectionDialog] still offers all eight.
 *
 * Switching is symmetric: LXST signals the peer with `PREFERRED_PROFILE + code`
 * and the peer's own stack follows, so this moves both ends of the call —
 * including Sideband and MeshChat peers.
 *
 * @param currentProfile codec the call is running on right now.
 * @param recommendedProfile codec the measured link suggests, or null.
 * @param onDismiss called when dismissed without a switch.
 * @param onProfileSelected called with the chosen codec.
 */
@Composable
fun InCallCodecDialog(
    currentProfile: CodecProfile,
    recommendedProfile: CodecProfile? = null,
    onDismiss: () -> Unit,
    onProfileSelected: (CodecProfile) -> Unit,
) {
    val options =
        CodecProfile.IN_CALL_TIERS.map { profile ->
            QualityOption(
                value = profile,
                displayName = profile.displayName,
                description = profile.description,
                isExperimental = profile.isExperimental,
                lcsBadge = profile.lcsRecommendation,
            )
        }

    // A call can be running on a profile outside the in-call set (dialled on
    // QUALITY_MAX or a LATENCY_* tier). Falling back keeps a row highlighted
    // instead of opening the dialog with nothing selected.
    val initial =
        if (currentProfile in CodecProfile.IN_CALL_TIERS) currentProfile else CodecProfile.DEFAULT

    QualitySelectionDialog(
        title = "Call Quality",
        subtitle = "Switching moves both ends of the call",
        options = options,
        initialSelection = initial,
        recommendedOption = recommendedProfile ?: initial,
        confirmButtonText = "Switch",
        onConfirm = onProfileSelected,
        onDismiss = onDismiss,
    )
}
