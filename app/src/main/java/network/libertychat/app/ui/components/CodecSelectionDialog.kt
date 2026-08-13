package network.columba.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import network.columba.app.service.ConversationLinkManager
import network.columba.app.ui.model.CodecProfile

/**
 * Dialog for selecting an audio codec profile before initiating a voice call.
 *
 * Displays all available codec profiles with their descriptions,
 * allowing the user to choose based on their network conditions.
 * Uses the generic QualitySelectionDialog for consistent UI with
 * other quality selection dialogs (e.g., image quality).
 *
 * @param recommendedProfile The recommended profile based on link speed (default: QUALITY_MEDIUM)
 * @param linkState Current link state for displaying path info (null to hide)
 * @param isProbing True while a link probe is in flight; the dialog renders
 *   immediately on open with a spinner inside the PathInfoSection, then the
 *   spinner is replaced with the probe result when the suspend completes.
 * @param onDismiss Called when the dialog is dismissed without selection
 * @param onProfileSelected Called with the selected profile and duplex mode
 *   when the user confirms. Half duplex defaults on when the probe lands on a
 *   low-bandwidth tier — see [CodecProfile.defaultsToHalfDuplex] — and is always
 *   a per-call choice, never a stored setting.
 */
@Composable
fun CodecSelectionDialog(
    recommendedProfile: CodecProfile = CodecProfile.DEFAULT,
    linkState: ConversationLinkManager.LinkState? = null,
    isProbing: Boolean = false,
    onDismiss: () -> Unit,
    onProfileSelected: (CodecProfile, Boolean) -> Unit,
) {
    // Keyed on recommendedProfile: the dialog opens before the probe finishes,
    // so the default has to re-derive when the recommendation lands.
    var halfDuplex by
        remember(recommendedProfile) {
            mutableStateOf(CodecProfile.defaultsToHalfDuplex(recommendedProfile))
        }

    val options =
        CodecProfile.entries.map { profile ->
            QualityOption(
                value = profile,
                displayName = profile.displayName,
                description = profile.description,
                isExperimental = profile.isExperimental,
                lcsBadge = profile.lcsRecommendation,
            )
        }

    QualitySelectionDialog(
        title = "Select Call Quality",
        subtitle = "Choose a codec profile based on your connection speed",
        options = options,
        initialSelection = recommendedProfile,
        recommendedOption = recommendedProfile,
        linkState = linkState,
        isProbing = isProbing,
        confirmButtonText = "Call",
        halfDuplex = halfDuplex,
        onHalfDuplexChange = { halfDuplex = it },
        onConfirm = { profile -> onProfileSelected(profile, halfDuplex) },
        onDismiss = onDismiss,
    )
}
